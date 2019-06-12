// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivitySubNames;
import com.intellij.diagnostic.ParallelActivity;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.gdpr.Consent;
import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.ide.gdpr.ConsentSettingsUi;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.Main;
import com.intellij.idea.SplashManager;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AppIcon.MacAppIcon;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
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
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public final class AppUIUtil {
  private static final String VENDOR_PREFIX = "jetbrains-";
  private static List<Image> ourIcons = null;
  private static volatile boolean ourMacDocIconSet = false;

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(AppUIUtil.class);
  }

  public static void updateWindowIcon(@NotNull Window window) {
    if (isWindowIconAlreadyExternallySet()) {
      return;
    }

    List<Image> images = ourIcons;
    if (images == null) {
      ourIcons = images = new ArrayList<>(3);

      ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
      String svgIconUrl = appInfo.getApplicationSvgIconUrl();
      ScaleContext ctx = ScaleContext.create(window);

      if (SystemInfo.isUnix) {
        @SuppressWarnings("deprecation") String fallback = appInfo.getBigIconUrl();
        ContainerUtil.addIfNotNull(images, loadApplicationIconImage(svgIconUrl, ctx, 128, fallback));
      }

      @SuppressWarnings("deprecation") String fallback = appInfo.getIconUrl();
      ContainerUtil.addIfNotNull(images, loadApplicationIconImage(svgIconUrl, ctx, 32, fallback));

      if (SystemInfo.isWindows) {
        ContainerUtil.addIfNotNull(images, loadSmallApplicationIconImage(ctx));
      }

      for (int i = 0; i < images.size(); i++) {
        Image image = images.get(i);
        if (image instanceof JBHiDPIScaledImage) {
          images.set(i, ((JBHiDPIScaledImage)image).getDelegate());
        }
      }
    }

    if (!images.isEmpty()) {
      if (!SystemInfo.isMac) {
        window.setIconImages(images);
      }
      else if (!ourMacDocIconSet && PluginManagerCore.isRunningFromSources()) {
        MacAppIcon.setDockIcon(ImageUtil.toBufferedImage(images.get(0)));
        ourMacDocIconSet = true;
      }
    }
  }

  public static boolean isWindowIconAlreadyExternallySet() {
    if (SystemInfo.isMac) {
      return ourMacDocIconSet || !PluginManagerCore.isRunningFromSources();
    }

    // todo[tav] 'jbre.win.app.icon.supported' is defined by JBRE, remove when OpenJDK supports it as well
    return SystemInfo.isWindows && Boolean.getBoolean("ide.native.launcher") && Boolean.getBoolean("jbre.win.app.icon.supported");
  }

  @NotNull
  private static Image loadSmallApplicationIconImage(@NotNull ScaleContext ctx) {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    @SuppressWarnings("deprecation") String fallbackSmallIconUrl = appInfo.getSmallIconUrl();
    return loadApplicationIconImage(appInfo.getSmallApplicationSvgIconUrl(), ctx, 16, fallbackSmallIconUrl);
  }

  @NotNull
  public static Icon loadSmallApplicationIcon(@NotNull ScaleContext ctx) {
    Image image = loadSmallApplicationIconImage(ctx);
    return new JBImageIcon(image);
  }

  /**
   * Returns a hidpi-aware image.
   */
  @Contract("_, _, _, !null -> !null")
  @Nullable
  private static Image loadApplicationIconImage(String svgPath, ScaleContext ctx, int size, String fallbackPath) {
    if (svgPath != null) {
      Icon icon = IconLoader.findIcon(svgPath);
      if (icon != null) {
        int width = icon.getIconWidth();
        float scale = size / (float)width;
        icon = IconUtil.scale(icon, null, scale); // performs vector scaling of a wrapped svg icon source
        return IconUtil.toImage(icon, ctx);
      }
      getLogger().info("Cannot load SVG application icon from " + svgPath);
    }

    if (fallbackPath != null) {
      return ImageLoader.loadFromResource(fallbackPath);
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

  public static void invokeOnEdt(Runnable runnable, @Nullable Condition<?> expired) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
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

  public static void updateFrameClass(@NotNull Toolkit toolkit) {
    if (SystemInfo.isWindows || SystemInfo.isMac) {
      return;
    }

    Activity activity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.UPDATE_FRAME_CLASS);
    try {
      Class<? extends Toolkit> aClass = toolkit.getClass();
      if ("sun.awt.X11.XToolkit".equals(aClass.getName())) {
        ReflectionUtil.setField(aClass, toolkit, null, "awtAppClassName", getFrameClass());
      }
    }
    catch (Exception ignore) { }
    activity.end();
  }

  // keep in sync with LinuxDistributionBuilder#getFrameClass
  public static String getFrameClass() {
    String name = StringUtil.toLowerCase(ApplicationNamesInfo.getInstance().getFullProductNameWithEdition())
      .replace(' ', '-')
      .replace("intellij-idea", "idea").replace("android-studio", "studio")  // backward compatibility
      .replace("-community-edition", "-ce").replace("-ultimate-edition", "").replace("-professional-edition", "");
    String wmClass = name.startsWith(VENDOR_PREFIX) ? name : VENDOR_PREFIX + name;
    if (PluginManagerCore.isRunningFromSources()) wmClass += "-debug";
    return wmClass;
  }

  public static void registerBundledFonts() {
    if (!SystemProperties.getBooleanProperty("ide.register.bundled.fonts", true)) {
      return;
    }

    String jvmVersion = System.getProperty("java.runtime.version");
    if (jvmVersion.startsWith("11.") && "JetBrains s.r.o".equals(System.getProperty("java.vm.vendor"))) {
      Matcher matcher = Pattern.compile("-b([\\d]+)(?:.([\\d])+)?$").matcher(jvmVersion);
      if (matcher.find() && Integer.parseInt(matcher.group(1)) >= 296) {
        return;
      }
    }

    Activity activity = ParallelActivity.PREPARE_APP_INIT.start(ActivitySubNames.REGISTER_BUNDLED_FONTS);

    File fontDir = PluginManagerCore.isRunningFromSources()
                   ? new File(PathManager.getCommunityHomePath(), "platform/platform-resources/src/fonts")
                   : null;

    registerFont("Inconsolata.ttf", fontDir);
    registerFont("SourceCodePro-Regular.ttf", fontDir);
    registerFont("SourceCodePro-Bold.ttf", fontDir);
    registerFont("SourceCodePro-It.ttf", fontDir);
    registerFont("SourceCodePro-BoldIt.ttf", fontDir);
    registerFont("FiraCode-Regular.ttf", fontDir);
    registerFont("FiraCode-Bold.ttf", fontDir);
    registerFont("FiraCode-Light.ttf", fontDir);
    registerFont("FiraCode-Medium.ttf", fontDir);
    registerFont("FiraCode-Retina.ttf", fontDir);
    activity.end();
  }

  private static void registerFont(@NotNull String name, @Nullable File fontDir) {
    try {
      Font font;
      if (fontDir == null) {
        URL url = AppUIUtil.class.getResource("/fonts/" + name);
        if (url == null) {
          getLogger().warn("Resource missing: " + name);
          return;
        }

        try (InputStream is = url.openStream()) {
          font = Font.createFont(Font.TRUETYPE_FONT, is);
        }
      }
      else {
        font = Font.createFont(Font.TRUETYPE_FONT, new File(fontDir, name));
      }
      GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
    }
    catch (Throwable t) {
      getLogger().warn("Cannot register font: " + name, t);
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
    String[] childFiles = ObjectUtils.notNull(new File(iconsPath).list(), ArrayUtilRt.EMPTY_STRING_ARRAY);

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

  /** @deprecated use {@link #showConsentsAgreementIfNeeded(Logger)} instead */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static boolean showConsentsAgreementIfNeed(@NotNull Logger log) {
    return showConsentsAgreementIfNeeded(log);
  }

  public static boolean showConsentsAgreementIfNeeded(@NotNull Logger log) {
    return showConsentsAgreementIfNeeded(command -> {
      if (EventQueue.isDispatchThread()) {
        command.run();
      }
      else {
        try {
          EventQueue.invokeAndWait(command);
        }
        catch (InterruptedException | InvocationTargetException e) {
          log.warn(e);
        }
      }
    });
  }

  public static boolean showConsentsAgreementIfNeeded(@NotNull Executor edtExecutor) {
    final Pair<List<Consent>, Boolean> consentsToShow = ConsentOptions.getInstance().getConsents();
    final Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    if (consentsToShow.second) {
      edtExecutor.execute(() -> result.set(confirmConsentOptions(consentsToShow.first)));
    }
    return result.get();
  }

  public static void updateForDarcula(boolean isDarcula) {
    JBColor.setDark(isDarcula);
    IconLoader.setUseDarkIcons(isDarcula);
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
        JPanel centerPanel = new JPanel(new BorderLayout(0, JBUIScale.scale(8)));
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
        styleSheet.addRule("body {font-size:" + JBUIScale.scaleFontSize((float)13) + "pt;}");
        styleSheet.addRule("h2, em {margin-top:" + JBUIScale.scaleFontSize((float)20) + "pt;}");
        styleSheet.addRule("h1, h2, h3, p, h4, em {margin-bottom:0;padding-bottom:0;}");
        styleSheet.addRule("p, h1 {margin-top:0;padding-top:" + JBUIScale.scaleFontSize((float)6) + "pt;}");
        styleSheet.addRule("li {margin-bottom:" + JBUIScale.scaleFontSize((float)6) + "pt;}");
        styleSheet.addRule("h2 {margin-top:0;padding-top:" + JBUIScale.scaleFontSize((float)13) + "pt;}");
        myViewer.setCaretPosition(0);
        myViewer.setBorder(JBUI.Borders.empty(0, 5, 5, 5));
        centerPanel.add(JBUI.Borders.emptyTop(8).wrap(
          new JLabel("Please read and accept these terms and conditions. Scroll down for full text:")), BorderLayout.NORTH);
        JBScrollPane scrollPane = new JBScrollPane(myViewer, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel(new BorderLayout());
        if (ApplicationInfoImpl.getShadowInstance().isEAP()) {
          JPanel eapPanel = new JPanel(new BorderLayout(8, 8));
          eapPanel.setBorder(JBUI.Borders.empty(8));
          //noinspection UseJBColor
          eapPanel.setBackground(new Color(0xDCE4E8));
          JLabel label = new JLabel(AllIcons.General.BalloonInformation);
          label.setVerticalAlignment(SwingConstants.TOP);
          eapPanel.add(label, BorderLayout.WEST);
          JEditorPane html = SwingHelper.createHtmlLabel(
            "EAP builds report usage statistics by default per "+
            (isPrivacyPolicy? "this Privacy Policy." : "the <a href=\"https://www.jetbrains.com/company/privacy.html\">JetBrains Privacy Policy</a>.") +
            "<br/>No personal or sensitive data are sent. You may disable this in the settings.", null, null
          );
          eapPanel.add(html, BorderLayout.CENTER);
          bottomPanel.add(eapPanel, BorderLayout.NORTH);
        }
        JCheckBox checkBox = new JCheckBox("I confirm that I have read and accept the terms of this User Agreement");
        bottomPanel.add(JBUI.Borders.empty(24, 0, 16, 0).wrap(checkBox), BorderLayout.CENTER);
        centerPanel.add(JBUI.Borders.emptyTop(8).wrap(bottomPanel), BorderLayout.SOUTH);
        checkBox.addActionListener(e -> setOKActionEnabled(checkBox.isSelected()));
        centerPanel.setPreferredSize(JBUI.size(520, 450));
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
        Application application = ApplicationManager.getApplication();
        if (application == null) {
          System.exit(Main.PRIVACY_POLICY_REJECTION);
        }
        else {
          ((ApplicationImpl)application).exit(true, true, false);
        }
      }
    };
    dialog.setModal(true);
    dialog.setTitle(
      isPrivacyPolicy
      ? ApplicationInfoImpl.getShadowInstance().getShortCompanyName() + " Privacy Policy"
      : ApplicationNamesInfo.getInstance().getFullProductName() + " User Agreement"
    );
    dialog.pack();

    SplashManager.executeWithHiddenSplash(dialog.getWindow(), () -> dialog.show());
  }

  public static boolean confirmConsentOptions(@NotNull List<Consent> consents) {
    if (consents.isEmpty()) {
      return false;
    }

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
      dialog.setSize(dialog.getWindow().getWidth(), dialog.getWindow().getHeight() + JBUIScale.scale(75));
    }
    dialog.show();

    int exitCode = dialog.getExitCode();
    if (exitCode == DialogWrapper.CANCEL_EXIT_CODE) {
      return false; //Don't save any changes in this case: user hasn't made a choice
    }

    final List<Consent> result;
    if (consents.size() == 1) {
      result = Collections.singletonList(consents.iterator().next().derive(exitCode == DialogWrapper.OK_EXIT_CODE));
    }
    else {
      result = new ArrayList<>();
      ui.apply(result);
    }
    saveConsents(result);
    return true;
  }

  public static List<Consent> loadConsentsForEditing() {
    final ConsentOptions options = ConsentOptions.getInstance();
    List<Consent> result = options.getConsents().first;
    if (options.isEAP()) {
      final Consent statConsent = options.getUsageStatsConsent();
      if (statConsent != null) {
        // init stats consent for EAP from the dedicated location
        final List<Consent> consents = result;
        result = new ArrayList<>();
        result.add(statConsent.derive(UsageStatisticsPersistenceComponent.getInstance().isAllowed()));
        result.addAll(consents);
      }
    }
    return result;
  }

  public static void saveConsents(List<Consent> consents) {
    final ConsentOptions options = ConsentOptions.getInstance();
    final Application app = ApplicationManager.getApplication();

    List<Consent> toSave = consents;

    if (app != null && options.isEAP()) {
      final Consent defaultStatsConsent = options.getUsageStatsConsent();
      if (defaultStatsConsent != null) {
        toSave = new ArrayList<>();
        for (Consent consent : consents) {
          if (defaultStatsConsent.getId().equals(consent.getId())) {
            UsageStatisticsPersistenceComponent.getInstance().setAllowed(consent.isAccepted());
          }
          else {
            toSave.add(consent);
          }
        }
      }
    }

    if (!toSave.isEmpty()) {
      options.setConsents(toSave);
    }
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