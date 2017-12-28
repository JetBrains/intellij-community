/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.gdpr.Consent;
import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.ide.gdpr.EndUserAgreement;
import com.intellij.idea.Main;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.AppIcon.MacAppIcon;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.basic.BasicRadioButtonUI;
import javax.swing.plaf.synth.SynthCheckBoxUI;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.text.DefaultCaret;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.List;

import static javax.swing.ScrollPaneConstants.*;

/**
 * @author yole
 */
public class AppUIUtil {
  private static final String VENDOR_PREFIX = "jetbrains-";
  private static final boolean DEBUG_MODE = SystemProperties.getBooleanProperty("idea.debug.mode", false);
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
      String bigIconUrl = appInfo.getBigIconUrl();
      if (bigIconUrl != null) {
        Image bigIcon = ImageLoader.loadFromResource(bigIconUrl);
        if (bigIcon != null) {
          images.add(bigIcon);
        }
      }
    }

    images.add(ImageLoader.loadFromResource(appInfo.getIconUrl()));
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
    String wmClass = VENDOR_PREFIX + name;
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

    try {
      InputStream is = url.openStream();
      try {
        Font font = Font.createFont(Font.TRUETYPE_FONT, is);
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
      }
      finally {
        is.close();
      }
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
  public static String findIcon(@NotNull String iconsPath) {
    String[] childFiles = ObjectUtils.notNull(new File(iconsPath).list(), ArrayUtil.EMPTY_STRING_ARRAY);

    // 1. look for .svg icon
    for (String child : childFiles) {
      if (child.endsWith(".svg")) {
        return iconsPath + '/' + child;
      }
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
          SwingUtilities.invokeAndWait(() -> showEndUserAgreementText(agreement.getText()));
          EndUserAgreement.setAccepted(agreement);
        }
        catch (Exception e) {
          Logger.getInstance(AppUIUtil.class).warn(e);
        }
      }
      final Pair<Collection<Consent>, Boolean> consentsToShow = ConsentOptions.getInstance().getConsents();
      if (consentsToShow.second) {
        try {
          final Ref<Collection<Consent>> result = Ref.create(null);
          // todo: does not seem to request focus when shown
          SwingUtilities.invokeAndWait(() -> result.set(confirmConsentOptions(consentsToShow.first)));
          final Collection<Consent> confirmed = result.get();
          if (confirmed != null) {
            ConsentOptions.getInstance().setConsents(confirmed);
          }
        }
        catch (Exception e) {
          Logger.getInstance(AppUIUtil.class).warn(e);
        }
      }
    }
  }

  /**
   * todo: update to support GDPR requirements
   *
   * @param htmlText Updated version of Privacy Policy text if any.
   *                 If it's {@code null}, the standard text from bundled resources would be used.
   */
  public static void showEndUserAgreementText(@NotNull String htmlText) {
    DialogWrapper dialog = new DialogWrapper(true) {
      @Override
      protected JComponent createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(JBUI.scale(5), JBUI.scale(5)));
        JEditorPane viewer = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
        viewer.setFocusable(true);
        viewer.addHyperlinkListener(new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(HyperlinkEvent e) {
            URL url = e.getURL();
            if (url != null) {
              BrowserUtil.browse(url);
            }
            else {
              SwingHelper.scrollToReference(viewer, e.getDescription());
            }
          }
        });
        viewer.setText(htmlText);
        StyleSheet styleSheet = ((HTMLDocument)viewer.getDocument()).getStyleSheet();
        styleSheet.addRule("body {font-family: \"Segoe UI\", Tahoma, sans-serif;}");
        styleSheet.addRule("body {margin-top:0;padding-top:0;}");
        styleSheet.addRule("body {font-size:" + JBUI.scaleFontSize(13) + "pt;}");
        styleSheet.addRule("h2, em {margin-top:" + JBUI.scaleFontSize(20) + "pt;}");
        styleSheet.addRule("h1, h2, h3, p, h4, em {margin-bottom:0;padding-bottom:0;}");
        styleSheet.addRule("p, h1 {margin-top:0;padding-top:"+JBUI.scaleFontSize(6)+"pt;}");
        styleSheet.addRule("li {margin-bottom:" + JBUI.scaleFontSize(6) + "pt;}");
        styleSheet.addRule("h2 {margin-top:0;padding-top:"+JBUI.scaleFontSize(13)+"pt;}");
        viewer.setCaretPosition(0);
        viewer.setBorder(JBUI.Borders.empty(0, 5, 5, 5));
        centerPanel.add(new JLabel("Please read and accept these terms and conditions:"), BorderLayout.NORTH);
        JBScrollPane scrollPane = new JBScrollPane(viewer, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
        final JScrollBar scrollBar = scrollPane.getVerticalScrollBar();
        scrollBar.addAdjustmentListener(new AdjustmentListener() {
          boolean wasScrolledToTheBottom = false;
          @Override
          public void adjustmentValueChanged(AdjustmentEvent e) {
            if (!wasScrolledToTheBottom) {
              wasScrolledToTheBottom = UIUtil.isScrolledToTheBottom(viewer);
            }
            setOKActionEnabled(wasScrolledToTheBottom);
          }
        });
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        return centerPanel;
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        init();
        setOKButtonText("Accept");
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
    dialog.setTitle(ApplicationNamesInfo.getInstance().getFullProductName() + " User License Agreement");
    dialog.setSize(JBUI.scale(509), JBUI.scale(395));
    dialog.show();
  }

  // todo: need a separate action to view and change state of all consets on demand
  
  public static Collection<Consent> confirmConsentOptions(@NotNull Collection<Consent> consents) {
    final Collection<Pair<JCheckBox, Consent>> consentMapping = new ArrayList<>();
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

        if (consents.isEmpty()) {
          JLabel label = new JLabel("There are no data-sharing options available", SwingConstants.CENTER);
          label.setVerticalAlignment(SwingConstants.CENTER);
          label.setOpaque(true);
          label.setBackground(JBColor.background());
          return label;
        }
        final JPanel body = new JPanel(new GridBagLayout());

        //noinspection UseDPIAwareInsets
        body.add(new JLabel("Please review your options regarding the sharing your data with JetBrains:"),
                 new GridBagConstraints(
                   0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                   new Insets(JBUI.scale(10), getLeftTextMargin(new JCheckBox()), JBUI.scale(10), 0), 0, 0));
        for (Iterator<Consent> it = consents.iterator(); it.hasNext(); ) {
          final Consent consent = it.next();
          final JComponent comp = createConsentElement(consent);
          boolean lastConsent = !it.hasNext();
          if (lastConsent) {
            body.setBackground(comp.getBackground());
          } else {
            comp.setBorder(JBUI.Borders.emptyBottom(15));
          }
          body.add(comp, new GridBagConstraints(
            0, GridBagConstraints.RELATIVE, 1, 1, 1.0, lastConsent ? 1.0 : 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, JBUI.insets(10, 0, 0, 0), 0, 0)
          );
        }
        body.setBorder(JBUI.Borders.empty(10));
        return new JBScrollPane(body, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
      }

      @NotNull
      private JComponent createConsentElement(Consent consent) {
        final JEditorPane viewer = SwingHelper.createHtmlViewer(true, null, JBColor.WHITE, JBColor.BLACK);
        viewer.setFocusable(false);
        viewer.setCaret(new DefaultCaret(){
          @Override
          protected void adjustVisibility(Rectangle nloc) {
            //do nothing to avoid autoscroll
          }
        });
        viewer.addHyperlinkListener(new HyperlinkAdapter() {
          @Override
          protected void hyperlinkActivated(HyperlinkEvent e) {
            final URL url = e.getURL();
            if (url != null) {
              BrowserUtil.browse(url);
            }
          }
        });
        viewer.setText(consentTextToHtml(consent.getText()));
        StyleSheet styleSheet = ((HTMLDocument)viewer.getDocument()).getStyleSheet();
        //styleSheet.addRule("body {font-family: \"Segoe UI\", Tahoma, sans-serif;}");
        styleSheet.addRule("body {margin-top:0;padding-top:0;}");
        //styleSheet.addRule("body {font-size:" + JBUI.scaleFontSize(13) + "pt;}");
        styleSheet.addRule("h2, em {margin-top:" + JBUI.scaleFontSize(20) + "pt;}");
        styleSheet.addRule("h1, h2, h3, p, h4, em {margin-bottom:0;padding-bottom:0;}");
        styleSheet.addRule("p, h1 {margin-top:0;padding-top:"+JBUI.scaleFontSize(6)+"pt;}");
        styleSheet.addRule("li {margin-bottom:" + JBUI.scaleFontSize(6) + "pt;}");
        styleSheet.addRule("h2 {margin-top:0;padding-top:"+JBUI.scaleFontSize(13)+"pt;}");
        viewer.setCaretPosition(0);

        final JCheckBox cb = new JBCheckBox(consent.getName(), consent.isAccepted());
        cb.setBackground(viewer.getBackground());
        cb.setFont(cb.getFont().deriveFont(Font.BOLD));
        int leftInset = getLeftTextMargin(cb);
        //noinspection UseDPIAwareBorders
        viewer.setBorder(new EmptyBorder(JBUI.scale(5), leftInset, 0, 0));

        final JPanel pane = new JPanel(new BorderLayout());
        pane.setBackground(viewer.getBackground());
        pane.add(cb, BorderLayout.NORTH);
        pane.add(viewer, BorderLayout.CENTER);
        consentMapping.add(Pair.create(cb, consent));
        return pane;
      }

      @NotNull
      private String consentTextToHtml(String text) {
        return "<html>" + StringUtil.replace(text, "\n", "<br>") + "</html>";
      }
      
      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        init();
        setAutoAdjustable(false);
      }

    };
    dialog.setModal(true);
    dialog.setTitle("Data Sharing Options");
    dialog.setSize(JBUI.scale(600), JBUI.scale(400));
    dialog.show();

    final Collection<Consent> result;
    if (dialog.isOK()) {
      result = new ArrayList<>();
      for (Pair<JCheckBox, Consent> pair : consentMapping) {
        result.add(pair.second.derive(pair.first.isSelected()));
      }
    }
    else {
      // no changes were made, save as-is
      result = consents;
    }
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
    AWTAccessor.getComponentAccessor().setGraphicsConfiguration(comp, gc);
  }

  /**
   * Returns distance (px) from the left edge to actual text position for specified checkbox.
   * It may be used as left margin when you need to align text in a label located above or below the checkbox
   */
  public static int getLeftTextMargin(@NotNull JCheckBox checkBox) {
    int leftMargin = 0;
    Insets margin = checkBox.getMargin();
    if (margin != null) leftMargin += margin.left;
    Border border = checkBox.getBorder();
    if (border != null) leftMargin += border.getBorderInsets(checkBox).left;
    ButtonUI ui = checkBox.getUI();
    Icon icon = null;
    if (ui instanceof BasicRadioButtonUI) {
      icon = ((BasicRadioButtonUI)ui).getDefaultIcon();
    } else if (ui instanceof SynthCheckBoxUI){
      SynthCheckBoxUI sui = (SynthCheckBoxUI)ui;
      SynthContext context = sui.getContext(checkBox);
      icon = context.getStyle().getIcon(context, "CheckBox.icon");
    }
    if (icon != null) {
      leftMargin += icon.getIconWidth() + checkBox.getIconTextGap();
    }
    return leftMargin;
  }
}