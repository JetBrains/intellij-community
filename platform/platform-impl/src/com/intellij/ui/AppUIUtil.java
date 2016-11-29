/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.PrivacyPolicy;
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.SwingHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Locale;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * @author yole
 */
public class AppUIUtil {
  private static final String VENDOR_PREFIX = "jetbrains-";

  public static void updateWindowIcon(@NotNull Window window) {
    window.setIconImages(getAppIconImages());
  }

  @SuppressWarnings({"UnnecessaryFullyQualifiedName", "deprecation"})
  private static List<Image> getAppIconImages() {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    List<Image> images = ContainerUtil.newArrayListWithCapacity(3);

    if (SystemInfo.isUnix) {//MacOS is Unix too
      String bigIconUrl = appInfo.getBigIconUrl();
      if (bigIconUrl != null) {
        images.add(com.intellij.util.ImageLoader.loadFromResource(bigIconUrl));
      }
    }

    images.add(com.intellij.util.ImageLoader.loadFromResource(appInfo.getIconUrl()));
    images.add(com.intellij.util.ImageLoader.loadFromResource(appInfo.getSmallIconUrl()));
    for (int i = 0; i < images.size(); i++) {
      Image image = images.get(i);
      if (image instanceof JBHiDPIScaledImage) {
        images.set(i, ((JBHiDPIScaledImage)image).getDelegate());
      }
    }
    return images;
  }

  public static void invokeLaterIfProjectAlive(@NotNull Project project, @NotNull Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
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
      final Toolkit toolkit = Toolkit.getDefaultToolkit();
      final Class<? extends Toolkit> aClass = toolkit.getClass();
      if ("sun.awt.X11.XToolkit".equals(aClass.getName())) {
        ReflectionUtil.setField(aClass, toolkit, null, "awtAppClassName", getFrameClass());
      }
    }
    catch (Exception ignore) { }
  }

  public static String getFrameClass() {
    String name = ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.US);
    String wmClass = VENDOR_PREFIX + StringUtil.replaceChar(name, ' ', '-');
    if ("true".equals(System.getProperty("idea.debug.mode"))) {
      wmClass += "-debug";
    }
    return PlatformUtils.isCommunityEdition() ? wmClass + "-ce" : wmClass;
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

  public static void showPrivacyPolicy() {
    if (ApplicationInfoImpl.getShadowInstance().isVendorJetBrains()) {
      Pair<PrivacyPolicy.Version, String> policy = PrivacyPolicy.getContent();
      if (!PrivacyPolicy.isVersionAccepted(policy.getFirst())) {
        try {
          SwingUtilities.invokeAndWait(() -> showPrivacyPolicyAgreement(policy.getSecond()));
          PrivacyPolicy.setVersionAccepted(policy.getFirst());
        }
        catch (Exception e) {
          Logger.getInstance(AppUIUtil.class).warn(e);
        }
      }
    }
  }

  /**
   * @param htmlText Updated version of Privacy Policy text if any.
   *                 If it's {@code null}, the standard text from bundled resources would be used.
   */
  public static void showPrivacyPolicyAgreement(@NotNull String htmlText) {
    DialogWrapper dialog = new DialogWrapper(true) {
      @Nullable
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
        centerPanel.add(new JBScrollPane(viewer, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        return centerPanel;
      }

      @Override
      protected void createDefaultActions() {
        super.createDefaultActions();
        init();
        setOKButtonText("Accept");
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
    dialog.setTitle(ApplicationNamesInfo.getInstance().getFullProductName() + " Privacy Policy Agreement");
    dialog.setSize(JBUI.scale(509), JBUI.scale(395));
    dialog.show();
  }
}