// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.ApplicationInfo;
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
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.PlatformUtils;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class PluginsAdvertiser {
  static final Logger LOG = Logger.getInstance(PluginsAdvertiser.class);
  private static final String CASHED_EXTENSIONS = "extensions.xml";

  public static final String IGNORE_ULTIMATE_EDITION = "ignoreUltimateEdition";
  public static final String IDEA_ULTIMATE_EDITION = "IntelliJ IDEA Ultimate Edition";
  public static final String ULTIMATE_EDITION_SUGGESTION = "Do not suggest Ultimate Edition";
  public static final String CHECK_ULTIMATE_EDITION_TITLE = "Check " + IDEA_ULTIMATE_EDITION;
  public static final String DISPLAY_ID = "Plugins Suggestion";
  public static final NotificationGroup NOTIFICATION_GROUP = new NotificationGroup(DISPLAY_ID, NotificationDisplayType.STICKY_BALLOON, true);

  private static SoftReference<KnownExtensions> ourKnownExtensions = new SoftReference<>(null);

  @Nullable
  public static List<Plugin> retrieve(UnknownFeature unknownFeature) {
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

  static void loadSupportedExtensions(@NotNull List<? extends IdeaPluginDescriptor> allPlugins) {
    final Map<String, IdeaPluginDescriptor> availableIds = new HashMap<>();
    for (IdeaPluginDescriptor plugin : allPlugins) {
      availableIds.put(plugin.getPluginId().getIdString(), plugin);
    }
    processFeatureRequest(
      ImmutableMap.of("featureType", FileTypeFactory.FILE_TYPE_FACTORY_EP.getName()),
      request -> {
        final JsonReader jsonReader = new JsonReader(request.getReader());
        jsonReader.setLenient(true);
        final JsonElement jsonRootElement = new JsonParser().parse(jsonReader);
        final Map<String, Set<Plugin>> result = new HashMap<>();
        for (JsonElement jsonElement : jsonRootElement.getAsJsonArray()) {
          final JsonObject jsonObject = jsonElement.getAsJsonObject();

          final String pluginId = StringUtil.unquoteString(jsonObject.get("pluginId").toString());
          final JsonElement bundledExt = jsonObject.get("bundled");
          boolean isBundled = Boolean.parseBoolean(bundledExt.toString());
          final IdeaPluginDescriptor fromServerPluginDescription = availableIds.get(pluginId);
          if (fromServerPluginDescription == null && !isBundled) continue;

          final IdeaPluginDescriptor loadedPlugin = PluginManager.getPlugin(PluginId.getId(pluginId));
          if (loadedPlugin != null && loadedPlugin.isEnabled()) continue;

          if (loadedPlugin != null && fromServerPluginDescription != null &&
              StringUtil.compareVersionNumbers(loadedPlugin.getVersion(), fromServerPluginDescription.getVersion()) >= 0) {
            continue;
          }

          if (fromServerPluginDescription != null && PluginManagerCore.isBrokenPlugin(fromServerPluginDescription)) continue;

          final JsonElement ext = jsonObject.get("implementationName");
          final String extension = StringUtil.unquoteString(ext.toString());
          Set<Plugin> pluginIds = result.get(extension);
          if (pluginIds == null) {
            pluginIds = new HashSet<>();
            result.put(extension, pluginIds);
          }
          final JsonElement pluginNameElement = jsonObject.get("pluginName");
          String pluginName = pluginNameElement != null ? StringUtil.unquoteString(pluginNameElement.toString()) : null;
          pluginIds.add(new Plugin(PluginId.getId(pluginId), pluginName, isBundled));
        }
        saveExtensions(result);
        return result;
      });
  }

  private static <K> K processFeatureRequest(Map<String, String> params, HttpRequests.RequestProcessor<K> requestProcessor) {
    String baseUrl = ApplicationInfoImpl.getShadowInstance().getPluginManagerUrl() + "/feature/getImplementations?";
    Url url = Urls.parseEncoded(baseUrl);
    if (url == null) {
      LOG.error("Cannot parse URL: " + baseUrl);
      return null;
    }
    return HttpRequests.request(url.addParameters(params)).productNameAsUserAgent().connect(requestProcessor, null, LOG);
  }

  public static void ensureDeleted() {
    FileUtilRt.delete(getExtensionsFile());
  }

  public static KnownExtensions loadExtensions() {
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
    BrowserUtil.browse(ApplicationInfo.getInstance().getCompanyURL());
  }

  static void enablePlugins(Project project, final Collection<IdeaPluginDescriptor> disabledPlugins) {
    PluginManagerConfigurableProxy.showPluginConfigurableAndEnable(project, disabledPlugins.toArray(new IdeaPluginDescriptor[0]));
  }

  @Nullable
  static IdeaPluginDescriptor getDisabledPlugin(Set<? extends Plugin> plugins) {
    for (Plugin plugin : plugins) {
      if (PluginManagerCore.isDisabled(plugin.myPluginId)) {
        return PluginManager.getPlugin(PluginId.getId(plugin.myPluginId));
      }
    }
    return null;
  }

  static List<String> hasBundledPluginToInstall(Collection<? extends Plugin> plugins) {
    if (PlatformUtils.isIdeaUltimate()) return null;
    final List<String> bundled = new ArrayList<>();
    for (Plugin plugin : plugins) {
      if (plugin.myBundled && PluginManager.getPlugin(PluginId.getId(plugin.myPluginId)) == null) {
        bundled.add(plugin.myPluginName != null ? plugin.myPluginName : plugin.myPluginId);
      }
    }
    return bundled.isEmpty() ? null : bundled;
  }

  public static void installAndEnablePlugins(final @NotNull Set<String> pluginIds, final @NotNull Runnable onSuccess) {
    ProgressManager.getInstance().run(new Task.Modal(null, "Search for Plugins in Repository", true) {
      private final Set<PluginDownloader> myPlugins = new HashSet<>();
      private List<IdeaPluginDescriptor> myAllPlugins;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          myAllPlugins = RepositoryHelper.loadPluginsFromAllRepositories(indicator);
          for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
            if (!descriptor.isEnabled() && pluginIds.contains(descriptor.getPluginId().getIdString())) {
              myPlugins.add(PluginDownloader.createDownloader(descriptor));
            }
          }
          for (IdeaPluginDescriptor loadedPlugin : myAllPlugins) {
            if (pluginIds.contains(loadedPlugin.getPluginId().getIdString())) {
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
      if (pluginSet != null) {
        return pluginSet.myPlugins;
      }
      return null;
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
    public String myPluginId;
    public String myPluginName;
    public boolean myBundled;

    public Plugin(PluginId pluginId, String pluginName, boolean bundled) {
      myPluginId = pluginId.getIdString();
      myBundled = bundled;
      myPluginName = pluginName;
    }

    public Plugin() {
    }

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

  static class ConfigurePluginsListener implements NotificationListener {
    private final Set<? extends UnknownFeature> myUnknownFeatures;
    private final Project myProject;
    private final List<? extends IdeaPluginDescriptor> myAllPlugins;
    private final Set<PluginDownloader> myPlugins;
    private final Map<Plugin, IdeaPluginDescriptor> myDisabledPlugins;

    ConfigurePluginsListener(Set<? extends UnknownFeature> unknownFeatures,
                             Project project,
                             List<? extends IdeaPluginDescriptor> allPlugins,
                             Set<PluginDownloader> plugins,
                             Map<Plugin, IdeaPluginDescriptor> disabledPlugins) {
      myUnknownFeatures = unknownFeatures;
      myProject = project;
      myAllPlugins = allPlugins;
      myPlugins = plugins;
      myDisabledPlugins = disabledPlugins;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        final String description = event.getDescription();
        if ("ignore".equals(description)) {
          UnknownFeaturesCollector featuresCollector = UnknownFeaturesCollector.getInstance(myProject);
          for (UnknownFeature feature : myUnknownFeatures) {
            featuresCollector.ignoreFeature(feature);
          }
          notification.expire();
        }
        else if ("configure".equals(description)) {
          LOG.assertTrue(myAllPlugins != null);
          notification.expire();
          new PluginsAdvertiserDialog(myProject, myPlugins.toArray(new PluginDownloader[0]), myAllPlugins).show();
        }
        else if ("enable".equals(description)) {
          enablePlugins(myProject, myDisabledPlugins.values());
          notification.expire();
        }
        else if ("ignoreUltimate".equals(description)) {
          PropertiesComponent.getInstance().setValue(IGNORE_ULTIMATE_EDITION, "true");
          notification.expire();
        }
        else if ("open".equals(description)) {
          openDownloadPage();
          notification.expire();
        }
      }
    }
  }
}