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
import com.intellij.ide.plugins.*;
import com.intellij.notification.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.*;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import org.jdom.Document;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;

public class PluginsAdvertiser implements StartupActivity {
  private static final Logger LOG = Logger.getInstance("#" + PluginsAdvertiser.class.getName());
  private static final String FEATURE_IMPLEMENTATIONS_URL = "http://plugins.jetbrains.com/feature/getImplementations?";
  private static final String CASHED_EXTENSIONS = "extensions.xml";

  public static List<PluginId> retrieve(UnknownFeature unknownFeature) {
    final String featureType = unknownFeature.getFeatureType();
    final String implementationName = unknownFeature.getImplementationName();
    final String buildNumber = ApplicationInfo.getInstance().getBuild().asString();
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
        final List<PluginId> result = new ArrayList<PluginId>();
        for (JsonElement jsonElement : jsonRootElement.getAsJsonArray()) {
          final JsonObject jsonObject = jsonElement.getAsJsonObject();
          final JsonElement pluginId = jsonObject.get("pluginId");
          result.add(PluginId.getId(StringUtil.unquoteString(pluginId.toString())));
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

  private static Map<String, Set<PluginId>> loadSupportedExtensions() {
    final String pluginRepositoryUrl = FEATURE_IMPLEMENTATIONS_URL + "featureType=" + FileTypeFactory.FILE_TYPE_FACTORY_EP.getName();
    try {
      HttpURLConnection connection = HttpConfigurable.getInstance().openHttpConnection(pluginRepositoryUrl);
      connection.connect();
      final InputStreamReader streamReader = new InputStreamReader(connection.getInputStream());
      try {
        final JsonReader jsonReader = new JsonReader(streamReader);
        jsonReader.setLenient(true);
        final JsonElement jsonRootElement = new JsonParser().parse(jsonReader);
        final Map<String, Set<PluginId>> result = new HashMap<String, Set<PluginId>>();
        for (JsonElement jsonElement : jsonRootElement.getAsJsonArray()) {
          final JsonObject jsonObject = jsonElement.getAsJsonObject();
          final JsonElement ext = jsonObject.get("implementationName");
          final String extension = StringUtil.unquoteString(ext.toString());
          Set<PluginId> pluginIds = result.get(extension);
          if (pluginIds == null) {
            pluginIds = new HashSet<PluginId>();
            result.put(extension, pluginIds);
          }
          pluginIds.add(PluginId.getId(StringUtil.unquoteString(jsonObject.get("pluginId").toString())));
        }
        saveExtensions(result);
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

  public static KnownExtensions loadExtensions() {
    try {
      File file = new File(PathManager.getPluginsPath(), CASHED_EXTENSIONS);
      if (file.isFile()) {
        final Document document = JDOMUtil.loadDocument(file);
        return XmlSerializer.deserialize(document, KnownExtensions.class);
      }
    }
    catch (Exception ignore) {
    }
    return null;
  }

  private static void saveExtensions(Map<String, Set<PluginId>> extensions) throws IOException {
    File plugins = new File(PathManager.getPluginsPath(), CASHED_EXTENSIONS);
    if (!plugins.isFile()) {
      FileUtil.ensureCanCreateFile(plugins);
    }
    plugins.deleteOnExit();
    JDOMUtil.writeDocument(new Document(XmlSerializer.serialize(new KnownExtensions(extensions))), plugins, "\n");
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

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            if (extensions == null) {
              loadSupportedExtensions();
            }
            int idx = 0;
            final Set<PluginId> ids = new HashSet<PluginId>();
            for (UnknownFeature feature : unknownFeatures) {
              indicator.setText("Searching for plugin supporting \'" + feature.getImplementationName() + "\'");
              final List<PluginId> pluginId = retrieve(feature);
              if (pluginId != null) {
                //do not suggest to download disabled plugins
                final List<String> disabledPlugins = PluginManagerCore.getDisabledPlugins();
                for (PluginId id : pluginId) {
                  if (!disabledPlugins.contains(id.getIdString())) {
                    ids.add(id);
                  }
                }
              }
              indicator.setFraction(((double) idx++) / unknownFeatures.size());
            }

            try {
              myAllPlugins = RepositoryHelper.loadPluginsFromRepository(indicator);
              for (IdeaPluginDescriptor loadedPlugin : myAllPlugins) {
                if (ids.contains(loadedPlugin.getPluginId())) {
                  myPlugins.add(PluginDownloader.createDownloader(loadedPlugin));
                }
              }
            }
            catch (Exception ignore) {
              //no notification to show
            }
          }

          @Override
          public void onSuccess() {
            if (!myPlugins.isEmpty()) {
              final String displayId = "Plugins Suggestion";
              new NotificationGroup(displayId, NotificationDisplayType.STICKY_BALLOON, true)
                .createNotification(displayId, "Features covered by non-bundled plugins are detected.<br>" +
                                               "<a href=\"configure\">Configure plugins...</a><br>" +
                                               "<a href=\"ignore\">Ignore All</a>", NotificationType.INFORMATION, new NotificationListener() {
                @Override
                public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
                  if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    final String description = event.getDescription();
                    if ("ignore".equals(description)) {
                      for (UnknownFeature feature : unknownFeatures) {
                        collectorSuggester.ignoreFeature(feature);
                      }
                      notification.expire();
                    } else if ("configure".equals(description)) {
                      LOG.assertTrue(myAllPlugins != null);
                      notification.expire();
                      new PluginsAdvertiserDialog(myProject, myPlugins.toArray(new PluginDownloader[myPlugins.size()]), myAllPlugins).show();
                    }
                  }
                }
              }).notify(project);
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

    public KnownExtensions(Map<String, Set<PluginId>> extensions) {
      for (String ext : extensions.keySet()) {
        myExtensions.put(ext, new PluginSet(extensions.get(ext)));
      }
    }

    public Set<String> find(String extension) {
      final PluginSet pluginSet = myExtensions.get(extension);
      if (pluginSet != null) {
        return pluginSet.myPlugins;
      }
      return null;
    }
  }

  @Tag("plugins")
  public static class PluginSet {
    public Set<String> myPlugins = new HashSet<String>();

    public PluginSet() {
    }

    public PluginSet(Set<PluginId> plugins) {
      for (PluginId plugin : plugins) {
        myPlugins.add(plugin.toString());
      }
    }
  }
}

