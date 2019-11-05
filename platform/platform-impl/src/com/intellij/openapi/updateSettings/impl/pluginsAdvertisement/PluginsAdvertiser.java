// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.PlatformUtils;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class PluginsAdvertiser {
  static final Logger LOG = Logger.getInstance(PluginsAdvertiser.class);
  private static final String CASHED_EXTENSIONS = "extensions.xml";

  public static final String IGNORE_ULTIMATE_EDITION = "ignoreUltimateEdition";
  public static final String IDEA_ULTIMATE_EDITION = "IntelliJ IDEA Ultimate";
  public static final String ULTIMATE_EDITION_SUGGESTION = "Do not suggest Ultimate";
  public static final String CHECK_ULTIMATE_EDITION_TITLE = "Try " + IDEA_ULTIMATE_EDITION;
  public static final String DISPLAY_ID = "Plugins Suggestion";
  public static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup(DISPLAY_ID, NotificationDisplayType.STICKY_BALLOON, true);
  public static final String FUS_GROUP_ID = "plugins.advertiser";

  private static SoftReference<KnownExtensions> ourKnownExtensions = new SoftReference<>(null);

  @Nullable
  public static List<Plugin> retrieve(UnknownFeature unknownFeature) throws IOException {
    final String featureType = unknownFeature.getFeatureType();
    final String implementationName = unknownFeature.getImplementationName();
    final String buildNumber = ApplicationInfo.getInstance().getApiVersion();
    return processFeatureRequest(
      ImmutableMap.of("featureType", featureType, "implementationName", implementationName, "build", buildNumber),
      request -> {
        final JsonReader jsonReader = new JsonReader(request.getReader());
        jsonReader.setLenient(true);
        final JsonElement jsonRootElement = new JsonParser().parse(jsonReader);
        final List<Plugin> result = new ArrayList<>();
        for (JsonElement jsonElement : jsonRootElement.getAsJsonArray()) {
          final JsonObject jsonObject = jsonElement.getAsJsonObject();
          final JsonElement pluginId = jsonObject.get("pluginId");
          final JsonElement pluginName = jsonObject.get("pluginName");
          final JsonElement bundled = jsonObject.get("bundled");
          result.add(new Plugin(PluginId.getId(StringUtil.unquoteString(pluginId.toString())),
                                pluginName != null ? StringUtil.unquoteString(pluginName.toString()) : null,
                                Boolean.parseBoolean(StringUtil.unquoteString(bundled.toString()))));
        }
        return result;
      });
  }

  static void loadSupportedExtensions(@NotNull List<? extends IdeaPluginDescriptor> allPlugins) throws IOException {
    Map<String, IdeaPluginDescriptor> availableIds = new HashMap<>();
    for (IdeaPluginDescriptor plugin : allPlugins) {
      availableIds.put(plugin.getPluginId().getIdString(), plugin);
    }
    @SuppressWarnings("deprecation") Map<String, String> params = ImmutableMap.of("featureType", FileTypeFactory.FILE_TYPE_FACTORY_EP.getName());
    processFeatureRequest(params, request -> {
      JsonReader jsonReader = new JsonReader(request.getReader());
      jsonReader.setLenient(true);
      JsonElement jsonRootElement = new JsonParser().parse(jsonReader);
      Map<String, Set<Plugin>> result = new HashMap<>();
      for (JsonElement jsonElement : jsonRootElement.getAsJsonArray()) {
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        String pluginId = StringUtil.unquoteString(jsonObject.get("pluginId").toString());
        JsonElement bundledExt = jsonObject.get("bundled");
        boolean isBundled = Boolean.parseBoolean(bundledExt.toString());
        IdeaPluginDescriptor fromServerPluginDescription = availableIds.get(pluginId);
        if (fromServerPluginDescription == null && !isBundled) continue;

        IdeaPluginDescriptor loadedPlugin = PluginManagerCore.getPlugin(PluginId.getId(pluginId));
        if (loadedPlugin != null && loadedPlugin.isEnabled()) continue;

        if (loadedPlugin != null && fromServerPluginDescription != null &&
            StringUtil.compareVersionNumbers(loadedPlugin.getVersion(), fromServerPluginDescription.getVersion()) >= 0) {
          continue;
        }

        if (fromServerPluginDescription != null && PluginManagerCore.isBrokenPlugin(fromServerPluginDescription)) continue;

        JsonElement ext = jsonObject.get("implementationName");
        String extension = StringUtil.unquoteString(ext.toString());
        Set<Plugin> pluginIds = result.get(extension);
        if (pluginIds == null) {
          pluginIds = new HashSet<>();
          result.put(extension, pluginIds);
        }
        JsonElement pluginNameElement = jsonObject.get("pluginName");
        String pluginName = pluginNameElement != null ? StringUtil.unquoteString(pluginNameElement.toString()) : null;
        pluginIds.add(new Plugin(PluginId.getId(pluginId), pluginName, isBundled));
      }
      saveExtensions(result);
      return result;
    });
  }

  private static <K> K processFeatureRequest(Map<String, String> params, HttpRequests.RequestProcessor<K> requestProcessor) throws IOException {
    String baseUrl = ApplicationInfoImpl.getShadowInstance().getPluginManagerUrl() + "/feature/getImplementations?";
    Url url = Urls.parseEncoded(baseUrl);
    if (url == null) {
      LOG.error("Cannot parse URL: " + baseUrl);
      return null;
    }
    return HttpRequests.request(url.addParameters(params)).productNameAsUserAgent().connect(requestProcessor);
  }

  static void ensureDeleted() {
    FileUtil.delete(getExtensionsFile());
  }

  public static @Nullable KnownExtensions loadExtensions() {
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

  private static void saveExtensions(Map<String, Set<Plugin>> extensions) throws IOException {
    File plugins = getExtensionsFile();
    if (!plugins.isFile()) {
      FileUtil.ensureCanCreateFile(plugins);
    }
    JDOMUtil.write(XmlSerializer.serialize(new KnownExtensions(extensions)), plugins);
  }

  public static void openDownloadPage() {
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl("https://www.jetbrains.com/idea/download/"));
  }

  static void enablePlugins(Project project, final Collection<IdeaPluginDescriptor> disabledPlugins) {
    PluginManagerConfigurable.showPluginConfigurableAndEnable(project, disabledPlugins.toArray(new IdeaPluginDescriptor[0]));
  }

  @Nullable
  static IdeaPluginDescriptor getDisabledPlugin(Set<? extends Plugin> plugins) {
    for (Plugin plugin : plugins) {
      if (PluginManagerCore.isDisabled(plugin.myPluginId)) {
        return PluginManagerCore.getPlugin(plugin.myPluginId);
      }
    }
    return null;
  }

  static List<PluginId> hasBundledPluginToInstall(Collection<? extends Plugin> plugins) {
    if (PlatformUtils.isIdeaUltimate()) {
      return null;
    }

    List<PluginId> bundled = new ArrayList<>();
    for (Plugin plugin : plugins) {
      if (plugin.myBundled && PluginManagerCore.getPlugin(plugin.myPluginId) == null) {
        bundled.add(plugin.myPluginName != null ? PluginId.getId(plugin.myPluginName) : plugin.myPluginId);
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
    ProgressManager.getInstance().run(new Task.Modal(null, "Search for Plugins in Repository", true) {
      private final Set<PluginDownloader> myPlugins = new HashSet<>();
      private List<IdeaPluginDescriptor> myAllPlugins;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          myAllPlugins = RepositoryHelper.loadPluginsFromAllRepositories(indicator);
          for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
            if (!descriptor.isEnabled() && pluginIds.contains(descriptor.getPluginId())) {
              myPlugins.add(PluginDownloader.createDownloader(descriptor));
            }
          }
          for (IdeaPluginDescriptor loadedPlugin : myAllPlugins) {
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
        if (myAllPlugins != null) {
          final PluginsAdvertiserDialog advertiserDialog =
            new PluginsAdvertiserDialog(null,
                                        myPlugins.toArray(new PluginDownloader[0]),
                                        myAllPlugins);
          if (advertiserDialog.showAndGet()) {
            onSuccess.run();
          }
        }
      }
    });
  }

  @Tag("exts")
  @SuppressWarnings("SpellCheckingInspection")
  public static class KnownExtensions {
    @OptionTag
    @XMap
    public Map<String, PluginSet> myExtensions = new HashMap<>();

    @SuppressWarnings("unused")
    public KnownExtensions() { }

    public KnownExtensions(Map<String, Set<Plugin>> extensions) {
      for (String ext : extensions.keySet()) {
        myExtensions.put(ext, new PluginSet(extensions.get(ext)));
      }
    }

    public Set<Plugin> find(String extension) {
      final PluginSet pluginSet = myExtensions.get(extension);
      return pluginSet != null ? pluginSet.myPlugins : null;
    }
  }

  @Tag("plugins")
  public static class PluginSet {
    public Set<Plugin> myPlugins = new HashSet<>();

    @SuppressWarnings("unused")
    public PluginSet() { }

    public PluginSet(Set<? extends Plugin> plugins) {
      myPlugins.addAll(plugins);
    }
  }

  @Tag("plugin")
  public static class Plugin implements Comparable<Plugin> {
    public PluginId myPluginId;
    public String myPluginName;
    public boolean myBundled;

    public Plugin(PluginId pluginId, String pluginName, boolean bundled) {
      myPluginId = pluginId;
      myBundled = bundled;
      myPluginName = pluginName;
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