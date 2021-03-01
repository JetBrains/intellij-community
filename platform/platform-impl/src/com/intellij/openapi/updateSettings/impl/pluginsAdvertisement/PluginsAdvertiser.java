// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.advertiser.KnownExtensions;
import com.intellij.ide.plugins.advertiser.KnownExtensionsService;
import com.intellij.ide.plugins.advertiser.PluginData;
import com.intellij.ide.plugins.marketplace.FeatureImpl;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EnumEventField;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.eventLog.events.EventId2;
import com.intellij.internal.statistic.eventLog.events.StringListEventField;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class PluginsAdvertiser {

  private static final Logger LOG = Logger.getInstance(PluginsAdvertiser.class);

  private static final @NonNls String IGNORE_ULTIMATE_EDITION = "ignoreUltimateEdition";

  /**
   * @deprecated Use {@link #getNotificationGroup()}
   */
  @Deprecated
  public static final NotificationGroup NOTIFICATION_GROUP = getNotificationGroup();

  private static final @NonNls String FUS_GROUP_ID = "plugins.advertiser";
  private static final EventLogGroup GROUP = new EventLogGroup(FUS_GROUP_ID,
                                                               1);

  public enum Source {EDITOR, NOTIFICATION}

  private static final EnumEventField<Source> SOURCE_FIELD = new EnumEventField<>("source",
                                                                                  Source.class,
                                                                                  source -> source.name().toLowerCase(Locale.ROOT));
  private static final StringListEventField PLUGINS_FIELD = new StringListEventField.ValidatedByCustomRule("plugins",
                                                                                                           "plugin");

  @SuppressWarnings("TypeParameterExtendsFinalClass")
  private static final @NotNull EventId2<List<? extends String>, Source> ENABLE_PLUGINS_EVENT = GROUP.registerEvent("enable.plugins",
                                                                                                                    PLUGINS_FIELD,
                                                                                                                    SOURCE_FIELD);
  @SuppressWarnings("TypeParameterExtendsFinalClass")
  private static final @NotNull EventId2<List<? extends String>, Source> INSTALL_PLUGINS_EVENT = GROUP.registerEvent("install.plugins",
                                                                                                                     PLUGINS_FIELD,
                                                                                                                     SOURCE_FIELD);

  private static final @NotNull EventId1<Source> CONFIGURE_PLUGINS_EVENT = GROUP.registerEvent("configure.plugins",
                                                                                               SOURCE_FIELD);

  private static final @NotNull EventId1<Source> IGNORE_ULTIMATE_EVENT = GROUP.registerEvent("ignore.ultimate",
                                                                                             SOURCE_FIELD);

  private static final @NotNull EventId1<Source> OPEN_DOWNLOAD_PAGE_EVENT = GROUP.registerEvent("open.download.page",
                                                                                                SOURCE_FIELD);

  private static final @NotNull EventId1<Source> IGNORE_EXTENSIONS_EVENT = GROUP.registerEvent("ignore.extensions",
                                                                                               SOURCE_FIELD);

  private static final @NotNull EventId1<Source> IGNORE_UNKNOWN_FEATURES_EVENT = GROUP.registerEvent("ignore.unknown.features",
                                                                                                     SOURCE_FIELD);

  public static @NotNull NotificationGroup getNotificationGroup() {
    return NotificationGroupManager.getInstance().getNotificationGroup("Plugins Suggestion");
  }

  static void logEnablePlugins(@NotNull List<@NonNls String> plugins,
                               @NotNull Source source,
                               @Nullable Project project) {
    ENABLE_PLUGINS_EVENT.log(project, plugins, source);
  }

  static void logInstallPlugins(@NotNull List<@NonNls String> plugins,
                                @SuppressWarnings("SameParameterValue") @NotNull Source source,
                                @SuppressWarnings("SameParameterValue") @Nullable Project project) {
    INSTALL_PLUGINS_EVENT.log(project, plugins, source);
  }

  public static void logConfigurePlugins(@NotNull Source source,
                                         @Nullable Project project) {
    CONFIGURE_PLUGINS_EVENT.log(project, source);
  }

  public static boolean isIgnoreUltimate() {
    return PropertiesComponent.getInstance().isTrueValue(IGNORE_ULTIMATE_EDITION);
  }

  public static void doIgnoreUltimateAndLog(@NotNull Source source,
                                            @Nullable Project project) {
    PropertiesComponent.getInstance().setValue(IGNORE_ULTIMATE_EDITION, true);
    IGNORE_ULTIMATE_EVENT.log(project, source);
  }

  static void openDownloadPageAndLog(@NotNull Source source,
                                     @Nullable Project project) {
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl("https://www.jetbrains.com/idea/download/"));
    OPEN_DOWNLOAD_PAGE_EVENT.log(project, source);
  }

  static void logIgnoreExtension(@SuppressWarnings("SameParameterValue") @NotNull Source source,
                                 @Nullable Project project) {
    IGNORE_EXTENSIONS_EVENT.log(project, source);
  }

  static void logIgnoreUnknownFeatures(@SuppressWarnings("SameParameterValue") @NotNull Source source,
                                       @NotNull Project project) {
    IGNORE_UNKNOWN_FEATURES_EVENT.log(project, source);
  }

  static @NotNull List<String> hasBundledPluginToInstall(@NotNull Collection<PluginData> plugins) {
    if (PlatformUtils.isIdeaUltimate()) {
      return Collections.emptyList();
    }

    Map<PluginId, IdeaPluginDescriptorImpl> descriptorsById = PluginManagerCore.buildPluginIdMap();
    return plugins.stream()
      .filter(PluginData::isBundled)
      .filter(plugin -> !descriptorsById.containsKey(plugin.getPluginId()))
      .map(PluginData::getPluginName)
      .collect(Collectors.toUnmodifiableList());
  }

  /**
   * @deprecated Use {@link #installAndEnable(Set, Runnable)}
   */
  @Deprecated
  public static void installAndEnablePlugins(@NotNull Set<String> pluginIds, @NotNull Runnable onSuccess) {
    installAndEnable(new LinkedHashSet<>(ContainerUtil.map(pluginIds, PluginId::getId)), onSuccess);
  }

  public static void installAndEnable(@NotNull Set<PluginId> pluginIds, @NotNull Runnable onSuccess) {
    installAndEnable(null, pluginIds, true, onSuccess);
  }

  public static void installAndEnable(@Nullable Project project,
                                      @NotNull Set<PluginId> pluginIds,
                                      boolean showDialog,
                                      @NotNull Runnable onSuccess) {
    ProgressManager.getInstance().run(new Task.Modal(project, IdeBundle.message("plugins.advertiser.task.searching.for.plugins"), true) {
      private final Set<PluginDownloader> myPlugins = new HashSet<>();
      private List<? extends IdeaPluginDescriptor> myRepositoryPlugins;
      private List<? extends IdeaPluginDescriptor> myCustomPlugins;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<String> ids = ContainerUtil.map(pluginIds, PluginId::getIdString);
          List<PluginNode> marketplacePlugins = MarketplaceRequests.getInstance().loadLastCompatiblePluginDescriptors(ids);
          myCustomPlugins = loadPluginsFromCustomRepositories(indicator);

          myRepositoryPlugins = UpdateChecker.mergePluginsFromRepositories(marketplacePlugins, myCustomPlugins, true);

          List<IdeaPluginDescriptor> descriptors = new ArrayList<>();
          for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
            if (!descriptor.isEnabled() && pluginIds.contains(descriptor.getPluginId()) && PluginManagerCore.isCompatible(descriptor)) {
              descriptors.add(descriptor);
            }
          }
          for (IdeaPluginDescriptor loadedPlugin : myRepositoryPlugins) {
            if (pluginIds.contains(loadedPlugin.getPluginId())) {
              descriptors.add(loadedPlugin);
            }
          }

          for (IdeaPluginDescriptor descriptor : descriptors) {
            myPlugins.add(PluginDownloader.createDownloader(descriptor));
          }
        }
        catch (Exception e) {
          LOG.info(e);
        }
      }

      @Override
      public void onSuccess() {
        if (myRepositoryPlugins == null) {
          return;
        }

        PluginsAdvertiserDialog advertiserDialog = new PluginsAdvertiserDialog(null,
                                                                               myPlugins.toArray(PluginDownloader.EMPTY_ARRAY),
                                                                               myCustomPlugins);
        advertiserDialog.setFinishFunction(result -> {
          if (result) {
            onSuccess.run();
          }
          return null;
        });
        if (showDialog) {
          advertiserDialog.showAndGet();
        }
        else {
          advertiserDialog.doInstallPlugins();
        }
      }
    });
  }

  /**
   * Loads list of plugins, compatible with a current build, from all configured repositories
   */
  private static @NotNull List<? extends IdeaPluginDescriptor> loadPluginsFromCustomRepositories(@Nullable ProgressIndicator indicator) {
    List<IdeaPluginDescriptor> descriptors = new ArrayList<>();
    for (String host : RepositoryHelper.getPluginHosts()) {
      if (host == null && ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) continue;

      try {
        descriptors.addAll(RepositoryHelper.loadPlugins(host, indicator));
      }
      catch (IOException e) {
        LOG.info("Couldn't load plugins from " + host + ": " + e);
        LOG.debug(e);
      }
    }

    Set<PluginId> addedPluginIds = new HashSet<>();
    return descriptors
      .stream()
      .filter(descriptor -> addedPluginIds.add(descriptor.getPluginId()))
      .collect(Collectors.toUnmodifiableList());
  }

  static final class BackgroundStartupActivity implements StartupActivity.Background {

    @Override
    public void runActivity(@NotNull Project project) {
      Application application = ApplicationManager.getApplication();
      if (application.isUnitTestMode() ||
          application.isHeadlessEnvironment() ||
          !UpdateSettings.getInstance().isPluginsCheckNeeded()) {
        return;
      }

      List<? extends IdeaPluginDescriptor> customPlugins = loadPluginsFromCustomRepositories(null);
      if (project.isDisposed()) {
        return;
      }

      KnownExtensionsService extensionsService = KnownExtensionsService.getInstance();
      KnownExtensions extensions = extensionsService.getExtensions();
      Set<UnknownFeature> unknownFeatures = UnknownFeaturesCollector.getInstance(project).getUnknownFeatures();
      if (extensions != null && unknownFeatures.isEmpty()) {
        return;
      }

      try {
        if (extensions == null) {
          Set<String> pluginIds = customPlugins
            .stream()
            .map(IdeaPluginDescriptor::getPluginId)
            .map(PluginId::getIdString)
            .collect(Collectors.toSet());
          extensionsService.setExtensions(getExtensionsFromMarketPlace(pluginIds));

          if (project.isDisposed()) {
            return;
          }
          EditorNotifications.getInstance(project).updateAllNotifications();
        }

        PluginAdvertiserService.getInstance().run(project,
                                                  customPlugins,
                                                  unknownFeatures);
      }
      catch (Exception e) {
        LOG.info(e);
      }
    }

    private static @NotNull KnownExtensions getExtensionsFromMarketPlace(@NotNull Set<String> pluginIds) {
      @SuppressWarnings("deprecation")
      Map<String, String> params = Map.of("featureType", FileTypeFactory.FILE_TYPE_FACTORY_EP.getName());
      Map<String, Set<PluginData>> extensionsMap = MarketplaceRequests.getInstance()
        .getFeatures(params)
        .stream()
        .collect(Collectors.groupingBy(FeatureImpl::getImplementationName,
                                       Collectors.flatMapping(feature -> Stream.ofNullable(feature.toPluginData(pluginIds::contains)),
                                                              Collectors.toSet())));
      return new KnownExtensions(extensionsMap);
    }
  }
}