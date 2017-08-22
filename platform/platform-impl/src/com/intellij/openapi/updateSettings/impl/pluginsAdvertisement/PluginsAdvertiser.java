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
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.IdeaApplication;
import com.intellij.notification.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.reference.SoftReference;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class PluginsAdvertiser implements StartupActivity {
  @NonNls public static final String IGNORE_ULTIMATE_EDITION = "ignoreUltimateEdition";
  private static final Logger LOG = Logger.getInstance(PluginsAdvertiser.class);
  private static final String CASHED_EXTENSIONS = "extensions.xml";

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
    return processFeatureRequest(ImmutableMap.of("featureType", featureType,
                                                 "implementationName", implementationName,
                                                 "build", buildNumber),
                                 new HttpRequests.RequestProcessor<List<Plugin>>() {
      @Override
      public List<Plugin> process(@NotNull HttpRequests.Request request) throws IOException {
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
      }
    }, null, LOG);
  }

  @Nullable
  private static Map<String, Set<Plugin>> loadSupportedExtensions(@NotNull List<IdeaPluginDescriptor> allPlugins) {
    final Map<String, IdeaPluginDescriptor> availableIds = new HashMap<>();
    for (IdeaPluginDescriptor plugin : allPlugins) {
      availableIds.put(plugin.getPluginId().getIdString(), plugin);
    }
    return processFeatureRequest(ImmutableMap.of("featureType", FileTypeFactory.FILE_TYPE_FACTORY_EP.getName()),
                                 new HttpRequests.RequestProcessor<Map<String, Set<Plugin>>>() {
      @Override
      public Map<String, Set<Plugin>> process(@NotNull HttpRequests.Request request) throws IOException {
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
          pluginIds.add(new Plugin(PluginId.getId(pluginId), pluginNameElement != null ? StringUtil.unquoteString(pluginNameElement.toString()) : null, isBundled));
        }
        saveExtensions(result);
        return result;
      }
    }, null, LOG);
  }

  private static <K> K processFeatureRequest(Map<String, String> params,
                                             HttpRequests.RequestProcessor<K> requestProcessor,
                                             K errorValue,
                                             Logger log) {
    URIBuilder uriBuilder;

    String baseUrl = ApplicationInfoImpl.getShadowInstance().getPluginManagerUrl() + "/feature/getImplementations?";
    try {
      uriBuilder = new URIBuilder(baseUrl);
    }
    catch (URISyntaxException e) {
      log.error(baseUrl, e);
      return errorValue;
    }
    params.forEach((key, value) -> uriBuilder.addParameter(key, value));

    boolean forceHttps = IdeaApplication.isLoaded() && UpdateSettings.getInstance().canUseSecureConnection();
    return HttpRequests.request(uriBuilder.toString()).forceHttps(forceHttps).productNameAsUserAgent().connect(requestProcessor, errorValue, LOG);
  }

  public static void ensureDeleted() {
    FileUtil.delete(getExtensionsFile());
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
    final PluginManagerConfigurable managerConfigurable = new PluginManagerConfigurable(PluginManagerUISettings.getInstance());
    final PluginManagerMain createPanel = managerConfigurable.getOrCreatePanel();
    ShowSettingsUtil.getInstance()
      .editConfigurable(project, managerConfigurable, () -> {
        final InstalledPluginsTableModel pluginsModel = (InstalledPluginsTableModel)createPanel.getPluginsModel();
        final IdeaPluginDescriptor[] descriptors = disabledPlugins.toArray(new IdeaPluginDescriptor[disabledPlugins.size()]);
        pluginsModel.enableRows(descriptors, Boolean.TRUE);
        createPanel.getPluginTable().select(descriptors);
      });
  }

  @Nullable
  static IdeaPluginDescriptor getDisabledPlugin(Set<Plugin> plugins) {
    final List<String> disabledPlugins = PluginManagerCore.getDisabledPlugins();
    for (Plugin plugin : plugins) {
      if (disabledPlugins.contains(plugin.myPluginId)) return PluginManager.getPlugin(PluginId.getId(plugin.myPluginId));
    }
    return null;
  }

  static List<String> hasBundledPluginToInstall(Collection<Plugin> plugins) {
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
                                        myPlugins.toArray(new PluginDownloader[myPlugins.size()]),
                                        myAllPlugins);
          if (advertiserDialog.showAndGet()) {
            onSuccess.run();
          }
        }
      }
    });
  }

  @Override
  public void runActivity(@NotNull Project project) {
    if (!UpdateSettings.getInstance().isCheckNeeded()) {
      return;
    }

    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      return;
    }

    application.executeOnPooledThread(new Runnable() {
      private final Set<PluginDownloader> myPlugins = new HashSet<>();
      private List<IdeaPluginDescriptor> myAllPlugins;

      private final Map<Plugin, IdeaPluginDescriptor> myDisabledPlugins = new HashMap<>();
      private List<String> myBundledPlugin;
      private final MultiMap<String, UnknownFeature> myFeatures = new MultiMap<>();
      private Set<UnknownFeature> unknownFeatures;

      @Override
      public void run() {
        if (project.isDisposed()) {
          return;
        }

        unknownFeatures = UnknownFeaturesCollector.getInstance(project).getUnknownFeatures();
        final KnownExtensions extensions = loadExtensions();
        if (extensions != null && unknownFeatures.isEmpty()) {
          return;
        }

        try {
          myAllPlugins = RepositoryHelper.loadPluginsFromAllRepositories(null);
          if (project.isDisposed()) {
            return;
          }
          if (extensions == null) {
            loadSupportedExtensions(myAllPlugins);
            if (project.isDisposed()) return;
            EditorNotifications.getInstance(project).updateAllNotifications();
          }
          final Map<String, Plugin> ids = new HashMap<>();
          for (UnknownFeature feature : unknownFeatures) {
            ProgressManager.checkCanceled();
            final List<Plugin> pluginId = retrieve(feature);
            if (pluginId != null) {
              for (Plugin plugin : pluginId) {
                ids.put(plugin.myPluginId, plugin);
                myFeatures.putValue(plugin.myPluginId, feature);
              }
            }
          }

          final List<String> disabledPlugins = PluginManagerCore.getDisabledPlugins();
          //include disabled plugins
          for (String id : ids.keySet()) {
            Plugin plugin = ids.get(id);
            if (disabledPlugins.contains(id)) {
              final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(PluginId.getId(id));
              if (pluginDescriptor != null) {
                myDisabledPlugins.put(plugin, pluginDescriptor);
              }
            }
          }

          myBundledPlugin = hasBundledPluginToInstall(ids.values());

          for (IdeaPluginDescriptor loadedPlugin : myAllPlugins) {
            final PluginId pluginId = loadedPlugin.getPluginId();
            if (ids.containsKey(pluginId.getIdString()) &&
                !disabledPlugins.contains(pluginId.getIdString()) &&
                !PluginManagerCore.isBrokenPlugin(loadedPlugin)) {
              myPlugins.add(PluginDownloader.createDownloader(loadedPlugin));
            }
          }

          ApplicationManager.getApplication().invokeLater(this::onSuccess, ModalityState.NON_MODAL);
        }
        catch (Exception e) {
          LOG.info(e);
        }
      }

      private void onSuccess() {
        String message = null;
        if (!myPlugins.isEmpty() || !myDisabledPlugins.isEmpty()) {
          message = getAddressedMessagePresentation();
          if (!myDisabledPlugins.isEmpty()) {
            message += "<a href=\"enable\">Enable plugins...</a><br>";
          }
          else {
            message += "<a href=\"configure\">Configure plugins...</a><br>";
          }

          message += "<a href=\"ignore\">Ignore Unknown Features</a>";
        }
        else if (myBundledPlugin != null && !PropertiesComponent.getInstance().isTrueValue(IGNORE_ULTIMATE_EDITION)) {
          message = "Features covered by " + IDEA_ULTIMATE_EDITION +
                    " (" + StringUtil.join(myBundledPlugin, ", ") + ") are detected.<br>" +
                    "<a href=\"open\">" + CHECK_ULTIMATE_EDITION_TITLE + "</a><br>" +
                    "<a href=\"ignoreUltimate\">" + ULTIMATE_EDITION_SUGGESTION + "</a>";
        }

        if (message != null) {
            final ConfigurePluginsListener notificationListener = new ConfigurePluginsListener(unknownFeatures, project, myAllPlugins, myPlugins, myDisabledPlugins);
          NOTIFICATION_GROUP.createNotification(DISPLAY_ID, message, NotificationType.INFORMATION, notificationListener).notify(project);
        }
      }

      @NotNull
      private String getAddressedMessagePresentation() {
        final MultiMap<String, String> addressedFeatures = MultiMap.createSet();
        final Set<String> ids = new LinkedHashSet<>();
        for (PluginDownloader plugin : myPlugins) {
          ids.add(plugin.getPluginId());
        }
        for (Plugin plugin : myDisabledPlugins.keySet()) {
          ids.add(plugin.myPluginId);
        }
        for (String id : ids) {
          for (UnknownFeature feature : myFeatures.get(id)) {
            addressedFeatures.putValue(feature.getFeatureDisplayName(), feature.getImplementationName());
          }
        }
        final String addressedFeaturesPresentation = StringUtil.join(addressedFeatures.entrySet(),
                                                                               entry -> entry.getKey() + "[" + StringUtil.join(entry.getValue(), ", ") + "]", ", ");
        final int addressedFeaturesNumber = addressedFeatures.keySet().size();
        final int pluginsNumber = ids.size();
        return StringUtil.pluralize("Plugin", pluginsNumber) + " supporting " + StringUtil.pluralize("feature", addressedFeaturesNumber) +
               " (" + addressedFeaturesPresentation + ") " + (pluginsNumber == 1 ? "is" : "are") + " currently " + (myPlugins.isEmpty() ? "disabled" : "not installed") + ".<br>";
      }
    });
  }

  @Tag("exts")
  public static class KnownExtensions {
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, PluginSet> myExtensions = new HashMap<>();

    @SuppressWarnings("unused")
    public KnownExtensions() {
    }

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
    public PluginSet() {
    }

    public PluginSet(Set<Plugin> plugins) {
      for (Plugin plugin : plugins) {
        myPlugins.add(plugin);
      }
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

  private static class ConfigurePluginsListener implements NotificationListener {
    private final Set<UnknownFeature> myUnknownFeatures;
    private final Project myProject;
    private final List<IdeaPluginDescriptor> myAllPlugins;
    private final Set<PluginDownloader> myPlugins;
    private final Map<Plugin, IdeaPluginDescriptor> myDisabledPlugins;

    public ConfigurePluginsListener(Set<UnknownFeature> unknownFeatures,
                                    Project project,
                                    List<IdeaPluginDescriptor> allPlugins,
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
          new PluginsAdvertiserDialog(myProject, myPlugins.toArray(new PluginDownloader[myPlugins.size()]), myAllPlugins).show();
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

