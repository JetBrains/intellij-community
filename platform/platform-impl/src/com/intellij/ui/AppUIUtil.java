// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.gdpr.Consent;
import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.ide.gdpr.ConsentSettingsUi;
import com.intellij.ide.gdpr.EndUserAgreement;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.Main;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AppIcon.MacAppIcon;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.ScaleContext;
import com.intellij.util.ui.SwingHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * @author yole
 */
public class AppUIUtil {
  private static final Logger LOG = Logger.getInstance(AppUIUtil.class);
  private static final String VENDOR_PREFIX = "jetbrains-";
  private static final boolean DEBUG_MODE = PluginManagerCore.isRunningFromSources();
  private static boolean ourMacDocIconSet = false;

  public static void updateWindowIcon(@NotNull Window window) {
    if (SystemInfo.isWindows &&
        SystemProperties.getBooleanProperty("ide.native.launcher", false) &&
        SystemProperties.getBooleanProperty("jbre.win.app.icon.supported", false)) // todo[tav] defined by JBRE, remove when OpenJDK supports it as well
    {
      return; // JDK will load icon from the exe resource
    }
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    List<Image> images = ContainerUtil.newArrayListWithCapacity(3);

    if (SystemInfo.isUnix) {
      Image svgIcon = loadApplicationIcon(window, 128, appInfo.getBigIconUrl());
      if (svgIcon != null) {
        images.add(svgIcon);
      }
    }

    images.add(loadApplicationIcon(window, 32, appInfo.getIconUrl()));
    images.add(ImageLoader.loadFromResource(appInfo.getSmallIconUrl()));

    for (int i = 0; i < images.size(); i++) {
      Image image = images.get(i);
      if (image instanceof JBHiDPIScaledImage) {
        images.set(i, ((JBHiDPIScaledImage)image).getDelegate());
      }
    }

    if (!images.isEmpty()) {
      if (!SystemInfo.isMac) {
        window.setIconImages(images);
      }
      else if (DEBUG_MODE && !ourMacDocIconSet) {
        MacAppIcon.setDockIcon(ImageUtil.toBufferedImage(images.get(0)));
        ourMacDocIconSet = true;
      }
    }
  }

  @Nullable
  private static Image loadApplicationIcon(@NotNull Window window, int size, @Nullable String fallbackImageResourcePath) {
    String svgIconUrl = ApplicationInfoImpl.getShadowInstance().getApplicationSvgIconUrl();
    if (svgIconUrl != null) {
      URL url = AppUIUtil.class.getResource(svgIconUrl);
      try {
        return
          SVGLoader.load(url, AppUIUtil.class.getResourceAsStream(svgIconUrl), ScaleContext.create(window), size, size);
      }
      catch (IOException e) {
        LOG.info("Cannot load svg application icon from " + svgIconUrl, e);
      }
    }
    else if (fallbackImageResourcePath != null) {
      Image image = ImageLoader.loadFromResource(fallbackImageResourcePath);
      if (image instanceof JBHiDPIScaledImage) {
        return ((JBHiDPIScaledImage)image).getDelegate();
      }
      return image;
    }
    return null;
  }

  public static void invokeLaterIfProjectAlive(@NotNull Project project, @NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      runnable.run();
    }
    else {
      application.invokeLater(runnable, o -> !project.isOpen() || project.isDisposed());
    }
  }

  public static void invokeOnEdt(Runnable runnable) {
    invokeOnEdt(runnable, null);
  }

  public static void invokeOnEdt(Runnable runnable, @Nullable Condition expired) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      //noinspection unchecked
      if (expired == null || !expired.value(null)) {
        runnable.run();
      }
    }
    else if (expired == null) {
      application.invokeLater(runnable);
    }
    else {
      application.invokeLater(runnable, expired);
    }
  }

  public static void updateFrameClass() {
    try {
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      Class<? extends Toolkit> aClass = toolkit.getClass();
      if ("sun.awt.X11.XToolkit".equals(aClass.getName())) {
        ReflectionUtil.setField(aClass, toolkit, null, "awtAppClassName", getFrameClass());
      }
    }
    catch (Exception ignore) { }
  }

  // keep in sync with LinuxDistributionBuilder#getFrameClass
  public static String getFrameClass() {
    String name = ApplicationNamesInfo.getInstance().getFullProductNameWithEdition()
      .toLowerCase(Locale.US)
      .replace(' ', '-')
      .replace("intellij-idea", "idea").replace("android-studio", "studio")  // backward compatibility
      .replace("-community-edition", "-ce").replace("-ultimate-edition", "").replace("-professional-edition", "");
    String wmClass = name.startsWith(VENDOR_PREFIX) ? name : VENDOR_PREFIX + name;
    if (DEBUG_MODE) wmClass += "-debug";
    return wmClass;
  }

  public static void registerBundledFonts() {
    if (SystemProperties.getBooleanProperty("ide.register.bundled.fonts", true)) {
      registerFont("/fonts/Inconsolata.ttf");
      registerFont("/fonts/SourceCodePro-Regular.ttf");
      registerFont("/fonts/SourceCodePro-Bold.ttf");
      registerFont("/fonts/SourceCodePro-It.ttf");
      registerFont("/fonts/SourceCodePro-BoldIt.ttf");
      registerFont("/fonts/FiraCode-Regular.ttf");
      registerFont("/fonts/FiraCode-Bold.ttf");
      registerFont("/fonts/FiraCode-Light.ttf");
      registerFont("/fonts/FiraCode-Medium.ttf");
      registerFont("/fonts/FiraCode-Retina.ttf");
    }
  }

  private static void registerFont(@NonNls String name) {
    URL url = AppUIUtil.class.getResource(name);
    if (url == null) {
      Logger.getInstance(AppUIUtil.class).warn("Resource missing: " + name);
      return;
    }

    try (InputStream is = url.openStream()) {
      Font font = Font.createFont(Font.TRUETYPE_FONT, is);
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
    }
    catch (Throwable t) {
      Logger.getInstance(AppUIUtil.class).warn("Cannot register font: " + url, t);
    }
  }

  public static void hideToolWindowBalloon(@NotNull String id, @NotNull Project project) {
    invokeLaterIfProjectAlive(project, () -> {
      Balloon balloon = ToolWindowManager.getInstance(project).getToolWindowBalloon(id);
      if (balloon != null) {
        balloon.hide();
      }
    });
  }

  private static final int MIN_ICON_SIZE = 32;

  @Nullable
  public static String findIcon() {
    String iconsPath = PathManager.getBinPath();
    String[] childFiles = ObjectUtils.notNull(new File(iconsPath).list(), ArrayUtil.EMPTY_STRING_ARRAY);

    // 1. look for .svg icon
    for (String child : childFiles) {
      if (child.endsWith(".svg")) {
        return iconsPath + '/' + child;
      }
    }

    File svgFile = ApplicationInfoEx.getInstanceEx().getApplicationSvgIconFile();
    if (svgFile != null) {
      return svgFile.getAbsolutePath();
    }

    // 2. look for .png icon of max size
    int best = MIN_ICON_SIZE - 1;
    String iconPath = null;
    for (String child : childFiles) {
      if (child.endsWith(".png")) {
        String path = iconsPath + '/' + child;
        Icon icon = new ImageIcon(path);
        int size = icon.getIconHeight();
        if (size > best && size == icon.getIconWidth()) {
          best = size;
          iconPath = path;
        }
      }
    }

    return iconPath;
  }

  public static void showUserAgreementAndConsentsIfNeeded() {
    if (ApplicationInfoImpl.getShadowInstance().isVendorJetBrains()) {
      EndUserAgreement.Document agreement = EndUserAgreement.getLatestDocument();
      if (!agreement.isAccepted()) {
        try {
          // todo: does not seem to request focus when shown
          SwingUtilities.invokeAndWait(() -> showEndUserAgreementText(agreement.getText(), agreement.isPrivacyPolicy()));
          EndUserAgreement.setAccepted(agreement);
        }
        catch (Exception e) {
          Logger.getInstance(AppUIUtil.class).warn(e);
        }
      }
      showConsentsAgreementIfNeed();
    }
  }

  public static boolean showConsentsAgreementIfNeed() {
    final Pair<List<Consent>, Boolean> consentsToShow = ConsentOptions.getInstance().getConsents();
    AtomicBoolean result = new AtomicBoolean();
    if (consentsToShow.second) {
      Runnable runnable = () -> {
        List<Consent> confirmed = confirmConsentOptions(consentsToShow.first);
        if (confirmed != null) {
          ConsentOptions.getInstance().setConsents(confirmed);
          result.set(true);
        }
      };
      if (SwingUtilities.isEventDispatchThread()) {
        runnable.run();
      } else {
        try {
          SwingUtilities.invokeAndWait(runnable);
        }
        catch (Exception e) {
          Logger.getInstance(AppUIUtil.class).warn(e);
        }
      }
    }
    return result.get();
  }

  /**
   * todo: update to support GDPR requirements
   *
   * @param htmlText Updated version of Privacy Policy or EULA text if any.
   *                 If it's {@code null}, the standard text from bundled resources would be used.
   * @param isPrivacyPolicy  true if this document is a privacy policy
   */
  public static void showEndUserAgreementText(@NotNull String htmlText, final boolean isPrivacyPolicy) {
    DialogWrapper dialog = new DialogWrapper(true) {

      private JEditorPane myViewer;

      @Override
      protected JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        myViewer = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
        myViewer.setFocusable(true);
        myViewer.addHyperlinkListener(new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(HyperlinkEvent e) {
            URL url = e.getURL();
            if (url != null) {
              BrowserUtil.browse(url);
            }
            else {
              SwingHelper.scrollToReference(myViewer, e.getDescription());
            }
          }
        });
        myViewer.setText(htmlText);
        StyleSheet styleSheet = ((HTMLDocument)myViewer.getDocument()).getStyleSheet();
        styleSheet.addRule("body {font-family: \"Segoe UI\", Tahoma, sans-serif;}");
        styleSheet.addRule("body {margin-top:0;padding-top:0;}");
        styleSheet.addRule("body {font-size:" + JBUI.scaleFontSize(13) + "pt;}");
        styleSheet.addRule("h2, em {margin-top:" + JBUI.scaleFontSize(20) + "pt;}");
        styleSheet.addRule("h1, h2, h3, p, h4, em {margin-bottom:0;padding-bottom:0;}");
        styleSheet.addRule("p, h1 {margin-top:0;padding-top:"+JBUI.scaleFontSize(6)+"pt;}");
        styleSheet.addRule("li {margin-bottom:" + JBUI.scaleFontSize(6) + "pt;}");
        styleSheet.addRule("h2 {margin-top:0;padding-top:"+JBUI.scaleFontSize(13)+"pt;}");
        myViewer.setCaretPosition(0);
        myViewer.setBorder(JBUI.Borders.empty(0, 5, 5, 5));
        centerPanel.add(JBUI.Borders.emptyTop(8).wrap(
          new JLabel("Please read and accept these terms and conditions. Scroll down for full text:")), BorderLayout.NORTH);
        JBScrollPane scrollPane = new JBScrollPane(myViewer, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        JCheckBox checkBox = new JCheckBox("I confirm that I have read and accept the terms of this User Agreement");
        centerPanel.add(JBUI.Borders.empty(24, 0, 16, 0).wrap(checkBox), BorderLayout.SOUTH);
        checkBox.addActionListener(e -> setOKActionEnabled(checkBox.isSelected()));
        return centerPanel;
      }

      @Nullable
      @Override
      public JComponent getPreferredFocusedComponent() {
        return myViewer;
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        init();
        setOKButtonText("Continue");
        setOKActionEnabled(false);
        setCancelButtonText("Reject and Exit");
        setAutoAdjustable(false);
      }

      @Override
      public void doCancelAction() {
        super.doCancelAction();
        ApplicationEx application = ApplicationManagerEx.getApplicationEx();
        if (application == null) {
          System.exit(Main.PRIVACY_POLICY_REJECTION);
        }
        else {
          ((ApplicationImpl)application).exit(true, true, false);
        }
      }
    };
    dialog.setModal(true);
    if (isPrivacyPolicy) {
      dialog.setTitle(ApplicationInfoImpl.getShadowInstance().getShortCompanyName() + " Privacy Policy");
    }
    else {
      dialog.setTitle(ApplicationNamesInfo.getInstance().getFullProductName() + " User Agreement");
    }
    dialog.setSize(JBUI.scale(509), JBUI.scale(395));
    dialog.show();
  }

  @Nullable
  public static List<Consent> confirmConsentOptions(@NotNull List<Consent> consents) {
    if (consents.isEmpty()) return null;

    ConsentSettingsUi ui = new ConsentSettingsUi(false);
    final DialogWrapper dialog = new DialogWrapper(true) {
      @Nullable
      @Override
      protected Border createContentPaneBorder() {
        return null;
      }

      @Nullable
      @Override
      protected JComponent createSouthPanel() {
        JComponent southPanel = super.createSouthPanel();
        if (southPanel != null) {
          southPanel.setBorder(ourDefaultBorder);
        }
        return southPanel;
      }

      @Override
      protected JComponent createCenterPanel() {
        return ui.getComponent();
      }

      @NotNull
      @Override
      protected Action[] createActions() {
        if (consents.size() > 1) {
          Action[] actions = super.createActions();
          setOKButtonText("Save");
          setCancelButtonText("Skip");
          return actions;
        }
        setOKButtonText(consents.iterator().next().getName());
        return new Action[]{getOKAction(), new DialogWrapperAction("Don't send") {
          @Override
          protected void doAction(ActionEvent e) {
            close(NEXT_USER_EXIT_CODE);
          }
        }};
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        init();
        setAutoAdjustable(false);
      }

    };
    ui.reset(consents);
    dialog.setModal(true);
    dialog.setTitle("Data Sharing");
    dialog.pack();
    if (consents.size() < 2) {
      dialog.setSize(dialog.getWindow().getWidth(), dialog.getWindow().getHeight() + JBUI.scale(75));
    }
    dialog.show();

    int exitCode = dialog.getExitCode();
    if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
      return null; //Don't save any changes in this case: user hasn't made a choice
    }
    if (consents.size() == 1) {
      consents.set(0, consents.get(0).derive(exitCode == DialogWrapper.OK_EXIT_CODE));
      return consents;
    }

    List<Consent> result = new ArrayList<>();
    ui.apply(result);
    return result;
  }

  /**
   * Targets the component to a (screen) device before showing.
   * In case the component is already a part of UI hierarchy (and is thus bound to a device)
   * the method does nothing.
   * <p>
   * The prior targeting to a device is required when there's a need to calculate preferred
   * size of a compound component (such as JEditorPane, for instance) which is not yet added
   * to a hierarchy. The calculation in that case may involve device-dependent metrics
   * (such as font metrics) and thus should refer to a particular device in multi-monitor env.
   * <p>
   * Note that if after calling this method the component is added to another hierarchy,
   * bound to a different device, AWT will throw IllegalArgumentException. To avoid that,
   * the device should be reset by calling {@code targetToDevice(comp, null)}.
   *
   * @param target the component representing the UI hierarchy and the target device
   * @param comp the component to target
   */
  public static void targetToDevice(@NotNull Component comp, @Nullable Component target) {
    if (comp.isShowing()) return;
    GraphicsConfiguration gc = target != null ? target.getGraphicsConfiguration() : null;
    setGraphicsConfiguration(comp, gc);
  }

  public static void setGraphicsConfiguration(@NotNull Component comp, @Nullable GraphicsConfiguration gc) {
    AWTAccessor.getComponentAccessor().setGraphicsConfiguration(comp, gc);
  }
}