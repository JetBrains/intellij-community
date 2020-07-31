// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.FeatureImpl;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class PluginsAdvertiser {
  static final Logger LOG = Logger.getInstance(PluginsAdvertiser.class);
  private static final String CASHED_EXTENSIONS = "extensions.xml";

  public static final String IGNORE_ULTIMATE_EDITION = "ignoreUltimateEdition";
  public static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup("Plugins Suggestion", NotificationDisplayType.STICKY_BALLOON, true);
  public static final String FUS_GROUP_ID = "plugins.advertiser";

  private static SoftReference<KnownExtensions> ourKnownExtensions = new SoftReference<>(null);
  private static boolean extensionsHaveBeenUpdated = false;

  @NotNull
  public static List<Plugin> retrieve(@NotNull UnknownFeature unknownFeature) {
    Map<String, String> params = new HashMap<>();
    params.put("featureType", unknownFeature.getFeatureType());
    params.put("implementationName", unknownFeature.getImplementationName());
    params.put("build", MarketplaceRequests.getInstance().getBuildForPluginRepositoryRequests());
    List<FeatureImpl> features =  MarketplaceRequests.getInstance().getFeatures(params);
    return ContainerUtil.map(features, feature ->
      new Plugin(
        feature.getPluginId() != null ? StringUtil.unquoteString(feature.getPluginId()) : null,
        feature.getPluginName() != null ? StringUtil.unquoteString(feature.getPluginName()) : null,
        feature.getBundled())
    );
  }

  static void loadAllExtensions(Set<String> customPluginIds) throws IOException {
    @SuppressWarnings("deprecation")
    Map<String, String> params = Collections.singletonMap("featureType", FileTypeFactory.FILE_TYPE_FACTORY_EP.getName());
    List<FeatureImpl> features =  MarketplaceRequests.getInstance().getFeatures(params);
    Map<String, Set<Plugin>> setExtensions = features.stream().collect(
      Collectors.groupingBy(FeatureImpl::getImplementationName, Collectors.mapping(
        feature -> new Plugin(
          feature.getPluginId() != null ? StringUtil.unquoteString(feature.getPluginId()) : null,
          feature.getPluginName() != null ? StringUtil.unquoteString(feature.getPluginName()) : null,
          feature.getBundled(),
          customPluginIds.contains(feature.getPluginId())
        ), Collectors.toSet()
      )));
    saveExtensions(setExtensions);
  }

  static void ensureDeleted() {
    FileUtil.delete(getExtensionsFile());
  }

  public static @Nullable KnownExtensions loadExtensions() {
    if (!extensionsHaveBeenUpdated) return null;
    KnownExtensions knownExtensions = ourKnownExtensions.get();
    if (knownExtensions != null) return knownExtensions;
    try {
      File file = getExtensionsFile();
      if (file.isFile()) {
        knownExtensions = XmlSerializer.deserialize(JDOMUtil.load(file), KnownExtensions.class);
        ourKnownExtensions = new SoftReference<>(knownExtensions);
        return knownExtensions;
      }
    }
    catch (Exception e) {
      LOG.info(e);
    }
    return null;
  }

  private static File getExtensionsFile() {
    return new File(PathManager.getPluginsPath(), CASHED_EXTENSIONS);
  }

  static void saveExtensions(Map<String, Set<Plugin>> extensions) throws IOException {
    File plugins = getExtensionsFile();
    if (!plugins.isFile()) {
      FileUtil.ensureCanCreateFile(plugins);
    }
    JDOMUtil.write(XmlSerializer.serialize(new KnownExtensions(extensions)), plugins);
    extensionsHaveBeenUpdated = true;
  }

  public static void openDownloadPage() {
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl("https://www.jetbrains.com/idea/download/"));
  }

  static void enablePlugins(Project project, final Collection<IdeaPluginDescriptor> disabledPlugins) {
    PluginManagerConfigurable.showPluginConfigurableAndEnable(project, disabledPlugins.toArray(new IdeaPluginDescriptor[0]));
  }

  static @Nullable List<String> hasBundledPluginToInstall(Collection<? extends Plugin> plugins) {
    if (PlatformUtils.isIdeaUltimate()) {
      return null;
    }

    List<String> bundled = new ArrayList<>();
    for (Plugin plugin : plugins) {
      if (plugin.myBundled && PluginManagerCore.getPlugin(PluginId.getId(plugin.myPluginId)) == null) {
        bundled.add(plugin.myPluginName != null ? plugin.myPluginName : plugin.myPluginId);
      }
    }
    return bundled.isEmpty() ? null : bundled;
  }

  /**
   * @deprecated Use {@link #installAndEnable(Set, Runnable)}
   */
  @Deprecated
  public static void installAndEnablePlugins(@NotNull Set<String> pluginIds, @NotNull Runnable onSuccess) {
    installAndEnable(new LinkedHashSet<>(ContainerUtil.map(pluginIds, it -> PluginId.getId(it))), onSuccess);
  }

  public static void installAndEnable(@NotNull Set<PluginId> pluginIds, @NotNull Runnable onSuccess) {
    installAndEnable(null, pluginIds, true, onSuccess);
  }

  public static void installAndEnable(@Nullable Project project, @NotNull Set<PluginId> pluginIds, boolean showDialog, @NotNull Runnable onSuccess) {
    ProgressManager.getInstance().run(new Task.Modal(project, IdeBundle.message("plugins.advertiser.task.searching.for.plugins"), true) {
      private final Set<PluginDownloader> myPlugins = new HashSet<>();
      private List<IdeaPluginDescriptor> myRepositoryPlugins;
      private List<IdeaPluginDescriptor> myCustomPlugins;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<@NotNull String> ids = ContainerUtil.map(pluginIds, id -> id.getIdString());
          List<PluginNode> marketplacePlugins =  MarketplaceRequests.getInstance().loadLastCompatiblePluginDescriptors(ids);
          myCustomPlugins = RepositoryHelper.loadPluginsFromCustomRepositories(indicator);

          myRepositoryPlugins = UpdateChecker.mergePluginsFromRepositories(marketplacePlugins, myCustomPlugins);

          for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
            if (!descriptor.isEnabled() && pluginIds.contains(descriptor.getPluginId()) && PluginManagerCore.isCompatible(descriptor)) {
              myPlugins.add(PluginDownloader.createDownloader(descriptor));
            }
          }
          for (IdeaPluginDescriptor loadedPlugin : myRepositoryPlugins) {
            if (pluginIds.contains(loadedPlugin.getPluginId())) {
              myPlugins.add(PluginDownloader.createDownloader(loadedPlugin));
            }
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

        PluginsAdvertiserDialog advertiserDialog =
          new PluginsAdvertiserDialog(null, myPlugins.toArray(new PluginDownloader[0]), myCustomPlugins);
        if (showDialog) {
          if (advertiserDialog.showAndGet()) {
            onSuccess.run();
          }
        } else {
          if (advertiserDialog.doInstallPlugins()) {
            onSuccess.run();
          }
        }
      }
    });
  }

  @Tag("exts")
  @SuppressWarnings("SpellCheckingInspection")
  public static final class KnownExtensions {
    @OptionTag
    @XMap
    public final Map<String, PluginSet> myExtensions = new HashMap<>();

    @SuppressWarnings("unused")
    public KnownExtensions() { }

    public KnownExtensions(@NotNull Map<String, Set<Plugin>> extensions) {
      for (String ext : extensions.keySet()) {
        myExtensions.put(ext, new PluginSet(extensions.get(ext)));
      }
    }

    public @Nullable Set<Plugin> find(@NotNull String extension) {
      PluginSet pluginSet = myExtensions.get(extension);
      return pluginSet != null ? pluginSet.myPlugins : null;
    }
  }

  @Tag("plugins")
  public static final class PluginSet {
    public final Set<Plugin> myPlugins = new HashSet<>();

    @SuppressWarnings("unused")
    public PluginSet() { }

    public PluginSet(Set<? extends Plugin> plugins) {
      myPlugins.addAll(plugins);
    }
  }

  @Tag("plugin")
  public static class Plugin implements Comparable<Plugin> {
    public String myPluginId;
    public String myPluginName;
    public boolean myBundled;
    public boolean myFromCustomRepository;

    /**
     * @deprecated Please use {@link #Plugin(String, String, boolean)}
     */
    @Deprecated
    public Plugin(PluginId pluginId, String pluginName, boolean bundled) {
      this(pluginId.getIdString(), pluginName, bundled);
    }

    public Plugin(String pluginId, String pluginName, boolean bundled) {
      myPluginId = pluginId;
      myBundled = bundled;
      myPluginName = pluginName;
    }

    public Plugin(String pluginId, String pluginName, boolean bundled, boolean isFromCustomRepository) {
      myPluginId = pluginId;
      myPluginName = pluginName;
      myBundled = bundled;
      myFromCustomRepository = isFromCustomRepository;
    }

    public Plugin() { }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Plugin plugin = (Plugin)o;

      if (myBundled != plugin.myBundled) return false;
      if (!myPluginId.equals(plugin.myPluginId)) return false;
      if (myPluginName != null && !myPluginName.equals(plugin.myPluginName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myPluginId.hashCode();
      result = 31 * result + (myBundled ? 1 : 0);
      result = 31 * result + (myPluginName != null ? myPluginName.hashCode() : 0);
      return result;
    }

    @Override
    public int compareTo(@NotNull Plugin other) {
      if (myBundled && !other.myBundled) return -1;
      if (!myBundled && other.myBundled) return 1;
      return Comparing.compare(myPluginId, other.myPluginId);
    }
  }
}