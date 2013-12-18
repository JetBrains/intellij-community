/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.plugins.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.*;

public class PluginsAdvertiser implements StartupActivity {
  @NonNls public static final String IGNORE_ULTIMATE_EDITION = "ignoreUltimateEdition";
  private static final Logger LOG = Logger.getInstance("#" + PluginsAdvertiser.class.getName());
  private static final String FEATURE_IMPLEMENTATIONS_URL = "http://plugins.jetbrains.com/feature/getImplementations?";
  private static final String CASHED_EXTENSIONS = "extensions.xml";

  public static final String ULTIMATE_EDITION_SUGGESTION = "Do not suggest Ultimate Edition";
  public static final String CHECK_ULTIMATE_EDITION_TITLE = "Check IntelliJ IDEA Ultimate Edition";

  public static List<Plugin> retrieve(UnknownFeature unknownFeature) {
    final String featureType = unknownFeature.getFeatureType();
    final String implementationName = unknownFeature.getImplementationName();
    final String buildNumber = ApplicationInfo.getInstance().getApiVersion();
    final String pluginRepositoryUrl = FEATURE_IMPLEMENTATIONS_URL +
                                       "featureType=" + featureType +
                                       "&implementationName=" + implementationName.replaceAll("#", "%23") +
                                       "&build=" + buildNumber;
    try {
      HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(pluginRepositoryUrl);
      connection.connect();
      final InputStreamReader streamReader = new InputStreamReader(connection.getInputStream());
      try {
        final JsonReader jsonReader = new JsonReader(streamReader);
        jsonReader.setLenient(true);
        final JsonElement jsonRootElement = new JsonParser().parse(jsonReader);
        final List<Plugin> result = new ArrayList<Plugin>();
        for (JsonElement jsonElement : jsonRootElement.getAsJsonArray()) {
          final JsonObject jsonObject = jsonElement.getAsJsonObject();
          final JsonElement pluginId = jsonObject.get("pluginId");
          final JsonElement bundled = jsonObject.get("bundled");
          result.add(new Plugin(PluginId.getId(StringUtil.unquoteString(pluginId.toString())), 
                                Boolean.parseBoolean(StringUtil.unquoteString(bundled.toString()))));
        }
        return result;
      }
      finally {
        streamReader.close();
      }
    }
    catch (IOException e) {
      LOG.info(e);
      return null;
    }
  }

  private static Map<String, Set<Plugin>> loadSupportedExtensions(@NotNull List<IdeaPluginDescriptor> allPlugins) {
    final Map<String, IdeaPluginDescriptor> availableIds = new HashMap<String, IdeaPluginDescriptor>();
    for (IdeaPluginDescriptor plugin : allPlugins) {
      availableIds.put(plugin.getPluginId().getIdString(), plugin);
    }
    final String pluginRepositoryUrl = FEATURE_IMPLEMENTATIONS_URL + "featureType=" + FileTypeFactory.FILE_TYPE_FACTORY_EP.getName();
    try {
      HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(pluginRepositoryUrl);
      connection.connect();
      final InputStreamReader streamReader = new InputStreamReader(connection.getInputStream());
      try {
        final JsonReader jsonReader = new JsonReader(streamReader);
        jsonReader.setLenient(true);
        final JsonElement jsonRootElement = new JsonParser().parse(jsonReader);
        final Map<String, Set<Plugin>> result = new HashMap<String, Set<Plugin>>();
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
              StringUtil.compareVersionNumbers(loadedPlugin.getVersion(), fromServerPluginDescription.getVersion()) >= 0) continue;

          final JsonElement ext = jsonObject.get("implementationName");
          final String extension = StringUtil.unquoteString(ext.toString());
          Set<Plugin> pluginIds = result.get(extension);
          if (pluginIds == null) {
            pluginIds = new HashSet<Plugin>();
            result.put(extension, pluginIds);
          }
          pluginIds.add(new Plugin(PluginId.getId(pluginId), isBundled));
        }
        saveExtensions(result);
        return result;
      }
      finally {
        streamReader.close();
      }
    }
    catch (Throwable e) {
      LOG.info(e);
      return null;
    }
  }

  public static void ensureDeleted() {
    FileUtil.delete(getExtensionsFile());
  }

  public static KnownExtensions loadExtensions() {
    try {
      File file = getExtensionsFile();
      if (file.isFile()) {
        final Document document = JDOMUtil.loadDocument(file);
        return XmlSerializer.deserialize(document, KnownExtensions.class);
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
    JDOMUtil.writeDocument(new Document(XmlSerializer.serialize(new KnownExtensions(extensions))), plugins, "\n");
  }

  public static void openDownloadPage() {
    BrowserUtil.open(ApplicationInfo.getInstance().getCompanyURL());    
  }

  static void enablePlugins(Project project, final Collection<IdeaPluginDescriptor> disabledPlugins) {
    final PluginManagerConfigurable managerConfigurable = new PluginManagerConfigurable(PluginManagerUISettings.getInstance());
    final PluginManagerMain createPanel = managerConfigurable.getOrCreatePanel();
    ShowSettingsUtil.getInstance()
      .editConfigurable(project, managerConfigurable, new Runnable() {
        @Override
        public void run() {
          final InstalledPluginsTableModel pluginsModel = (InstalledPluginsTableModel)createPanel.getPluginsModel();
          final IdeaPluginDescriptor[] descriptors = disabledPlugins.toArray(new IdeaPluginDescriptor[disabledPlugins.size()]);
          pluginsModel.enableRows(descriptors, Boolean.TRUE);
          createPanel.getPluginTable().select(descriptors);
        }
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

  static boolean hasBundledNotInstalledPlugin(Collection<Plugin> plugins) {
    for (Plugin plugin : plugins) {
      if (plugin.myBundled && PluginManager.getPlugin(PluginId.getId(plugin.myPluginId)) == null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    if (!UpdateSettings.getInstance().CHECK_NEEDED) return;
    final UnknownFeaturesCollector collectorSuggester = UnknownFeaturesCollector.getInstance(project);
    final Set<UnknownFeature> unknownFeatures = collectorSuggester.getUnknownFeatures();
    final KnownExtensions extensions = loadExtensions();
    if (extensions != null && unknownFeatures.isEmpty()) return;
    final Runnable runnable = new Runnable() {
      public void run() {
        final Application application = ApplicationManager.getApplication();
        if (application.isUnitTestMode() || application.isHeadlessEnvironment()) return;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Search for non-bundled plugins in plugin repository...") {
          private final Set<PluginDownloader> myPlugins = new HashSet<PluginDownloader>();
          private List<IdeaPluginDescriptor> myAllPlugins;

          private Map<Plugin, IdeaPluginDescriptor> myDisabledPlugins = new HashMap<Plugin, IdeaPluginDescriptor>();
          private boolean myBundledPlugins = false;

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            try {
              myAllPlugins = RepositoryHelper.loadPluginsFromRepository(indicator);
              if (project.isDisposed()) return;
              if (extensions == null) {
                loadSupportedExtensions(myAllPlugins);
                EditorNotifications.getInstance(project).updateAllNotifications();
              }
              int idx = 0;
              final Map<String, Plugin> ids = new HashMap<String, Plugin>();
              for (UnknownFeature feature : unknownFeatures) {
                indicator.setText("Searching for plugin supporting \'" + feature.getImplementationName() + "\'");
                ProgressManager.checkCanceled();
                final List<Plugin> pluginId = retrieve(feature);
                if (pluginId != null) {
                  for (Plugin plugin : pluginId) {
                    ids.put(plugin.myPluginId, plugin);
                  }
                }
                indicator.setFraction(((double)idx++) / unknownFeatures.size());
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

              myBundledPlugins = hasBundledNotInstalledPlugin(ids.values());

              for (IdeaPluginDescriptor loadedPlugin : myAllPlugins) {
                final PluginId pluginId = loadedPlugin.getPluginId();
                if (ids.containsKey(pluginId.getIdString()) && !disabledPlugins.contains(pluginId.getIdString())) {
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
            String message = null;
            if (!myPlugins.isEmpty() || !myDisabledPlugins.isEmpty()) {
              message = "Features covered by non-bundled plugins are detected.<br>";

              if (!myDisabledPlugins.isEmpty()) {
                message += "<a href=\"enable\">Enable plugins...</a><br>";
              }
              else {
                message += "<a href=\"configure\">Configure plugins...</a><br>";
              }

              message += "<a href=\"ignore\">Ignore All</a>";
            }
            else if (myBundledPlugins && !PropertiesComponent.getInstance().isTrueValue(IGNORE_ULTIMATE_EDITION)) {
              message = "Features covered by IntelliJ IDEA Ultimate Edition are detected.<br>" +
                        "<a href=\"open\">" + CHECK_ULTIMATE_EDITION_TITLE + "</a><br>" +
                        "<a href=\"ignoreUltimate\">" + ULTIMATE_EDITION_SUGGESTION + "</a>";
            }

            if (message != null) {
              final String displayId = "Plugins Suggestion";
              final ConfigurePluginsListener notificationListener = new ConfigurePluginsListener(unknownFeatures, project, myAllPlugins, myPlugins, myDisabledPlugins);
              new NotificationGroup(displayId, NotificationDisplayType.STICKY_BALLOON, true)
                                   .createNotification(displayId, message, NotificationType.INFORMATION, notificationListener).notify(project);
            }
          }
        });
      }
    };

    SwingUtilities.invokeLater(runnable);
  }

  @Tag("exts")
  public static class KnownExtensions {
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    public Map<String, PluginSet> myExtensions = new HashMap<String, PluginSet>();

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
    public Set<Plugin> myPlugins = new HashSet<Plugin>();

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
    public boolean myBundled;

    public Plugin(PluginId pluginId, boolean bundled) {
      myPluginId = pluginId.getIdString();
      myBundled = bundled;
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

      return true;
    }

    @Override
    public int hashCode() {
      int result = myPluginId.hashCode();
      result = 31 * result + (myBundled ? 1 : 0);
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

