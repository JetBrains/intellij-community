// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.gdpr.Consent;
import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.ide.gdpr.ConsentSettingsUi;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.AppIcon.MacAppIcon;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.ui.scale.ScaleContextAware;
import com.intellij.util.*;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

public final class AppUIUtil {
  private static final String VENDOR_PREFIX = "jetbrains-";
  private static List<Image> ourIcons = null;
  private static volatile boolean ourMacDocIconSet = false;

  private static @NotNull Logger getLogger() {
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
      String smallSvgIconUrl = appInfo.getSmallApplicationSvgIconUrl();
      ScaleContext scaleContext = ScaleContext.create(window);

      if (SystemInfoRt.isUnix) {
        @SuppressWarnings("deprecation")
        Image image = loadApplicationIconImage(svgIconUrl, scaleContext, 128, appInfo.getBigIconUrl());
        if (image != null) {
          images.add(image);
        }
      }

      @SuppressWarnings("deprecation")
      Image element = loadApplicationIconImage(smallSvgIconUrl, scaleContext, 32, appInfo.getIconUrl());
      if (element != null) {
        images.add(element);
      }

      if (SystemInfoRt.isWindows) {
        //noinspection deprecation
        images.add(loadApplicationIconImage(smallSvgIconUrl, scaleContext, 16, appInfo.getSmallIconUrl()));
      }

      for (int i = 0; i < images.size(); i++) {
        Image image = images.get(i);
        if (image instanceof JBHiDPIScaledImage) {
          images.set(i, ((JBHiDPIScaledImage)image).getDelegate());
        }
      }
    }

    if (!images.isEmpty()) {
      if (!SystemInfoRt.isMac) {
        window.setIconImages(images);
      }
      else if (!ourMacDocIconSet) {
        MacAppIcon.setDockIcon(ImageUtil.toBufferedImage(images.get(0)));
        ourMacDocIconSet = true;
      }
    }
  }

  public static boolean isWindowIconAlreadyExternallySet() {
    if (SystemInfoRt.isMac) {
      return ourMacDocIconSet || (!PlatformUtils.isCodeWithMeGuest() && !PluginManagerCore.isRunningFromSources());
    }

    // todo[tav] JBR supports loading icon resource (id=2000) from the exe launcher, remove when OpenJDK supports it as well
    return SystemInfoRt.isWindows && Boolean.getBoolean("ide.native.launcher") && SystemInfo.isJetBrainsJvm;
  }

  public static @NotNull Icon loadSmallApplicationIcon(@NotNull ScaleContext scaleContext) {
    return loadSmallApplicationIcon(scaleContext, 16);
  }

  public static @NotNull Icon loadSmallApplicationIcon(@NotNull ScaleContext scaleContext, int size) {
    return loadSmallApplicationIcon(scaleContext, size, !ApplicationInfoImpl.getShadowInstance().isEAP());
  }

  public static @NotNull Icon loadSmallApplicationIconForRelease(@NotNull ScaleContext scaleContext, int size) {
    return loadSmallApplicationIcon(scaleContext, size, true);
  }

  private static @NotNull Icon loadSmallApplicationIcon(@NotNull ScaleContext scaleContext, int size, boolean isReleaseIcon) {
    ApplicationInfoEx appInfo = ApplicationInfoImpl.getShadowInstance();
    String smallIconUrl = appInfo.getSmallApplicationSvgIconUrl();

    //this is a way to load the release icon in EAP. Need for some actions.
    if (isReleaseIcon && appInfo.isEAP()) {
      if (appInfo instanceof ApplicationInfoImpl) {
        smallIconUrl = ((ApplicationInfoImpl)appInfo).getSmallApplicationSvgIconUrl(false);
      }
    }

    Icon icon = smallIconUrl == null ? null : loadApplicationIcon(smallIconUrl, scaleContext, size);
    if (icon != null) {
      return icon;
    }

    @SuppressWarnings("deprecation") String fallbackSmallIconUrl = appInfo.getSmallIconUrl();
    Image image = ImageLoader.loadFromResource(fallbackSmallIconUrl, AppUIUtil.class);
    assert image != null : "Can't load '" + fallbackSmallIconUrl + "'";
    icon = new JBImageIcon(image);
    return scaleIconToSize(icon, size);
  }

  public static @Nullable Icon loadApplicationIcon(@NotNull ScaleContext ctx, int size) {
    String url = ApplicationInfoImpl.getShadowInstance().getApplicationSvgIconUrl();
    return url == null ? null : loadApplicationIcon(url, ctx, size);
  }

  /**
   * Returns a hidpi-aware image.
   */
  @Contract("_, _, _, !null -> !null")
  private static @Nullable Image loadApplicationIconImage(@Nullable String svgPath, ScaleContext scaleContext, int size, @Nullable String fallbackPath) {
    Icon icon = svgPath == null ? null : loadApplicationIcon(svgPath, scaleContext, size);
    if (icon != null) {
      return IconLoader.toImage(icon, scaleContext);
    }

    if (fallbackPath != null) {
      return ImageLoader.loadFromResource(fallbackPath, AppUIUtil.class);
    }
    return null;
  }

  private static @Nullable Icon loadApplicationIcon(@NotNull String svgPath, ScaleContext scaleContext, int size) {
    Icon icon = IconLoader.findIcon(svgPath, AppUIUtil.class.getClassLoader());
    if (icon == null) {
      getLogger().info("Cannot load SVG application icon from " + svgPath);
      return null;
    }

    if (icon instanceof ScaleContextAware) {
      ((ScaleContextAware)icon).updateScaleContext(scaleContext);
    }
    return scaleIconToSize(icon, size);
  }

  private static @NotNull Icon scaleIconToSize(Icon icon, int size) {
    int width = icon.getIconWidth();
    if (width == size) return icon;

    float scale = size / (float)width;
    icon = IconUtil.scale(icon, null, scale);
    return icon;
  }

  public static void invokeLaterIfProjectAlive(@NotNull Project project, @NotNull Runnable runnable) {
    Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      if (project.isOpen() && !project.isDisposed()) {
        runnable.run();
      }
    }
    else {
      application.invokeLater(runnable, __ -> !project.isOpen() || project.isDisposed());
    }
  }

  public static void invokeOnEdt(Runnable runnable) {
    invokeOnEdt(runnable, null);
  }

  /**
   * @deprecated Use {@link com.intellij.openapi.application.AppUIExecutor#expireWith(Disposable)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public static void invokeOnEdt(@NotNull Runnable runnable, @Nullable Condition<?> expired) {
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
    if (SystemInfoRt.isWindows || SystemInfoRt.isMac) {
      return;
    }

    try {
      Class<? extends Toolkit> aClass = toolkit.getClass();
      if ("sun.awt.X11.XToolkit".equals(aClass.getName())) {
        ReflectionUtil.setField(aClass, toolkit, null, "awtAppClassName", getFrameClass());
      }
    }
    catch (Exception ignore) {
    }
  }

  // keep in sync with LinuxDistributionBuilder#getFrameClass
  public static String getFrameClass() {
    String name = Strings.toLowerCase(ApplicationNamesInfo.getInstance().getFullProductNameWithEdition())
      .replace(' ', '-')
      .replace("intellij-idea", "idea").replace("android-studio", "studio")  // backward compatibility
      .replace("-community-edition", "-ce").replace("-ultimate-edition", "").replace("-professional-edition", "");
    String wmClass = name.startsWith(VENDOR_PREFIX) ? name : VENDOR_PREFIX + name;
    if (PluginManagerCore.isRunningFromSources()) {
      wmClass += "-debug";
    }
    return wmClass;
  }

  private static final int MIN_ICON_SIZE = 32;

  public static @Nullable String findIcon() {
    String iconsPath = PathManager.getBinPath();
    String[] childFiles = ObjectUtils.notNull(new File(iconsPath).list(), ArrayUtilRt.EMPTY_STRING_ARRAY);

    // 1. look for .svg icon
    for (String child : childFiles) {
      if (child.endsWith(".svg")) {
        return iconsPath + '/' + child;
      }
    }

    String svgIconUrl = ApplicationInfoImpl.getShadowInstance().getApplicationSvgIconUrl();
    if (svgIconUrl != null) {
      URL url = ApplicationInfoEx.class.getResource(svgIconUrl);
      if (url != null && URLUtil.FILE_PROTOCOL.equals(url.getProtocol())) {
        return URLUtil.urlToFile(url).getAbsolutePath();
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

  public static boolean needToShowConsentsAgreement() {
    return ConsentOptions.getInstance().getConsents().second;
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

  public static boolean confirmConsentOptions(@NotNull List<Consent> consents) {
    if (consents.isEmpty()) {
      return false;
    }

    ConsentSettingsUi ui = new ConsentSettingsUi(false);
    final DialogWrapper dialog = new DialogWrapper(true) {
      @Override
      protected @Nullable Border createContentPaneBorder() {
        return null;
      }

      @Override
      protected @Nullable JComponent createSouthPanel() {
        JComponent southPanel = super.createSouthPanel();
        if (southPanel != null) {
          southPanel.setBorder(createDefaultBorder());
        }
        return southPanel;
      }

      @Override
      protected JComponent createCenterPanel() {
        return ui.getComponent();
      }

      @Override
      protected Action @NotNull [] createActions() {
        if (consents.size() > 1) {
          Action[] actions = super.createActions();
          setOKButtonText(IdeBundle.message("button.save"));
          setCancelButtonText(IdeBundle.message("button.skip"));
          return actions;
        }
        setOKButtonText(consents.iterator().next().getName());
        return new Action[]{getOKAction(), new DialogWrapperAction(IdeBundle.message("button.don.t.send")) {
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
    dialog.setTitle(IdeBundle.message("dialog.title.data.sharing"));
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
    if (comp.isShowing()) {
      return;
    }
    GraphicsConfiguration gc = target != null ? target.getGraphicsConfiguration() : null;
    AWTAccessor.getComponentAccessor().setGraphicsConfiguration(comp, gc);
  }

  public static boolean isInFullscreen(@Nullable Window window) {
    return window instanceof IdeFrame && ((IdeFrame)window).isInFullScreen();
  }

  public static Object adjustFractionalMetrics(Object defaultValue) {
    if (!SystemInfoRt.isMac || GraphicsEnvironment.isHeadless()) {
      return defaultValue;
    }

    GraphicsConfiguration gc =
      GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    return (JBUIScale.sysScale(gc) == 1.0f)? RenderingHints.VALUE_FRACTIONALMETRICS_OFF : defaultValue;
  }
}