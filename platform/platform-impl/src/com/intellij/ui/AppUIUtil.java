// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.gdpr.Consent;
import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.ide.gdpr.ConsentSettingsUi;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.*;
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
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

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
        Image image = loadApplicationIconImage(svgIconUrl, scaleContext, 128, null);
        if (image != null) {
          images.add(image);
        }
      }

      Image element = loadApplicationIconImage(smallSvgIconUrl, scaleContext, 32, null);
      if (element != null) {
        images.add(element);
      }

      if (SystemInfoRt.isWindows) {
        @SuppressWarnings("deprecation") Image image = loadApplicationIconImage(smallSvgIconUrl, scaleContext, 16, appInfo.getSmallIconUrl());
        images.add(image);
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
      return ourMacDocIconSet || (!PlatformUtils.isJetBrainsClient() && !PluginManagerCore.isRunningFromSources());
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

    if (isReleaseIcon && appInfo.isEAP() && appInfo instanceof ApplicationInfoImpl) {
      // This is the way to load the release icon in EAP. Needed for some actions.
      smallIconUrl = ((ApplicationInfoImpl)appInfo).getSmallApplicationSvgIconUrl(false);
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

  public static void updateFrameClass() {
    if (SystemInfoRt.isWindows || SystemInfoRt.isMac) {
      return;
    }

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
    String name = ApplicationNamesInfo.getInstance().getFullProductNameWithEdition().toLowerCase(Locale.ENGLISH)
      .replace(' ', '-')
      .replace("intellij-idea", "idea").replace("android-studio", "studio")  // backward compatibility
      .replace("-community-edition", "-ce").replace("-ultimate-edition", "").replace("-professional-edition", "");
    String wmClass = name.startsWith(VENDOR_PREFIX) ? name : VENDOR_PREFIX + name;
    if (PluginManagerCore.isRunningFromSources()) wmClass += "-debug";
    return wmClass;
  }

  public static @Nullable String findIcon() {
    String binPath = PathManager.getBinPath();
    String[] binFiles = new File(binPath).list();

    if (binFiles != null) {
      for (String child : binFiles) {
        if (child.endsWith(".svg")) {
          return binPath + '/' + child;
        }
      }
    }

    String svgIconUrl = ApplicationInfoImpl.getShadowInstance().getApplicationSvgIconUrl();
    if (svgIconUrl != null) {
      URL url = ApplicationInfoEx.class.getResource(svgIconUrl);
      if (url != null && URLUtil.FILE_PROTOCOL.equals(url.getProtocol())) {
        return URLUtil.urlToFile(url).getAbsolutePath();
      }
    }

    return null;
  }

  public static boolean showConsentsAgreementIfNeeded(@NotNull Logger log) {
    return showConsentsAgreementIfNeeded(log, __ -> true);
  }
  
  public static boolean showConsentsAgreementIfNeeded(@NotNull Logger log, Predicate<Consent> filter) {
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
    }, filter);
  }

  private static boolean showConsentsAgreementIfNeeded(@NotNull Executor edtExecutor, Predicate<Consent> filter) {
    Pair<List<Consent>, Boolean> consentsToShow = ConsentOptions.getInstance().getConsents(filter);
    Ref<Boolean> result = new Ref<>(Boolean.FALSE);
    if (consentsToShow.getSecond()) {
      edtExecutor.execute(() -> result.set(confirmConsentOptions(consentsToShow.getFirst())));
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
    DialogWrapper dialog = new DialogWrapper(true) {
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
        return new Action[]{getOKAction(), new DialogWrapperAction(IdeBundle.message("button.do.not.send")) {
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
      return false;  // don't save any changes in this case: a user hasn't made a choice
    }

    List<Consent> result;
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
    ConsentOptions options = ConsentOptions.getInstance();
    List<Consent> result = options.getConsents().getFirst();
    if (options.isEAP()) {
      Consent statConsent = options.getDefaultUsageStatsConsent();
      if (statConsent != null) {
        // init stats consent for EAP from the dedicated location
        List<Consent> consents = result;
        result = new ArrayList<>();
        result.add(statConsent.derive(UsageStatisticsPersistenceComponent.getInstance().isAllowed()));
        result.addAll(consents);
      }
    }
    return result;
  }

  public static void saveConsents(List<Consent> consents) {
    if (consents.isEmpty()) {
      return;
    }

    ConsentOptions options = ConsentOptions.getInstance();
    if (ApplicationManager.getApplication() != null && options.isEAP()) {
      Predicate<Consent> isUsageStats = ConsentOptions.condUsageStatsConsent();
      int saved = 0;
      for (Consent consent : consents) {
        if (isUsageStats.test(consent)) {
          UsageStatisticsPersistenceComponent.getInstance().setAllowed(consent.isAccepted());
          saved++;
        }
      }
      if (consents.size() - saved > 0) {
        List<Consent> list = new ArrayList<>();
        for (Consent consent : consents) {
          if (!isUsageStats.test(consent)) {
            list.add(consent);
          }
        }
        options.setConsents(list);
      }
    }
    else {
      options.setConsents(consents);
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

  public static boolean isInFullScreen(@Nullable Window window) {
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
