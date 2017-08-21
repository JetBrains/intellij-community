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
package org.jetbrains.idea.devkit.gradle;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.plugins.gradle.frameworkSupport.BuildScriptDataBuilder;
import org.jetbrains.plugins.gradle.frameworkSupport.GradleFrameworkSupportProvider;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;

import javax.swing.*;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleIntellijPluginFrameworkSupportProvider extends GradleFrameworkSupportProvider {
  private static final String ID = "gradle-intellij-plugin";
  private static final Logger LOG = Logger.getInstance(GradleIntellijPluginFrameworkSupportProvider.class);

  private static final String LATEST_GRADLE_VERSION_KEY = "LATEST_GRADLE_VERSION_KEY";
  private static final String LATEST_UPDATING_TIME_KEY = "LATEST_UPDATING_TIME_KEY";

  private static class Lazy {
    static final ExecutorService EXECUTOR = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("UPDATE_GRADLE_PLUGIN_VERSIONS");
  }

  @NotNull
  @Override
  public FrameworkTypeEx getFrameworkType() {
    return new FrameworkTypeEx(ID) {
      @NotNull
      @Override
      public FrameworkSupportInModuleProvider createProvider() {
        return GradleIntellijPluginFrameworkSupportProvider.this;
      }

      @NotNull
      @Override
      public String getPresentableName() {
        return "IntelliJ Platform Plugin";
      }

      @NotNull
      @Override
      public Icon getIcon() {
        return AllIcons.Nodes.Plugin;
      }
    };
  }

  @Override
  public void addSupport(@NotNull Module module,
                         @NotNull ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider,
                         @NotNull BuildScriptDataBuilder buildScriptData) {
    String pluginVersion = PropertiesComponent.getInstance().getValue(LATEST_GRADLE_VERSION_KEY, "0.2.13");
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
    String ideVersion;
    if (applicationInfo.isEAP()) {
      BuildNumber build = applicationInfo.getBuild();
      ideVersion = build.isSnapshot() ? build.getBaselineVersion() + "-SNAPSHOT" : build.asStringWithoutProductCode();
    }
    else {
      ideVersion = applicationInfo.getFullVersion();
    }
    buildScriptData
      .addPluginDefinitionInPluginsGroup("id 'org.jetbrains.intellij' version '" + pluginVersion + "'")
      .addOther("intellij {\n    version '" + ideVersion + "'\n}\n")
      .addOther("patchPluginXml {\n" +
                "    pluginId \"${group}.${project.name}\"\n" +
                "    changeNotes \"\"\"\n" +
                "      Add change notes here.<br>\n" +
                "      <em>most HTML tags may be used</em>\"\"\"\n" +
                "    description \"\"\"\n" +
                "      Enter short description for your plugin here.<br>\n" +
                "      <em>most HTML tags may be used</em>\"\"\"\n" +
                "}");
    VirtualFile contentRoot = ArrayUtil.getFirstElement(rootModel.getContentRoots());
    if (contentRoot == null) return;
    if (!createPluginXml(module, contentRoot.getPath())) return;
    createRunConfiguration(module, contentRoot.getPath());
    StartupManager.getInstance(module.getProject())
      .runWhenProjectIsInitialized(
        () -> FileEditorManager.getInstance(module.getProject()).openFile(buildScriptData.getBuildScriptFile(), true));
  }

  @Override
  public JComponent createComponent() {
    // checking new gradle version on creating component
    String latestVersion = PropertiesComponent.getInstance().getValue(LATEST_GRADLE_VERSION_KEY);
    long timeCheck = PropertiesComponent.getInstance().getOrInitLong(LATEST_UPDATING_TIME_KEY, System.currentTimeMillis());
    if (latestVersion == null || TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timeCheck) >= 1) {
      ModalityState modalityState = ModalityState.defaultModalityState();
      Lazy.EXECUTOR.submit(() -> {
        try {
          // sadly plugins.gradle.org has no API and doesn't support meta-versions like latest.
          // Let's parse HTML with REGEXPs muhahaha
          String content = HttpRequests.request("https://plugins.gradle.org/plugin/org.jetbrains.intellij")
            .productNameAsUserAgent()
            .readString(new EmptyProgressIndicator(modalityState));
          Matcher matcher = Pattern.compile("Version ([\\d.]+) \\(latest\\)").matcher(content);
          if (matcher.find()) {
            PropertiesComponent.getInstance().setValue(LATEST_GRADLE_VERSION_KEY, matcher.group(1));
            PropertiesComponent.getInstance().setValue(LATEST_UPDATING_TIME_KEY, String.valueOf(System.currentTimeMillis()));
          }
        }
        catch (IOException ignore) {

        }
      });
    }
    return null;
  }

  private boolean createPluginXml(@NotNull Module module, @NotNull String contentRootPath) {
    try {
      VirtualFile metaInf = VfsUtil.createDirectoryIfMissing(contentRootPath + "/src/main/resources/META-INF");
      if (metaInf == null) {
        return false;
      }
      if (metaInf.findChild("plugin.xml") != null) {
        return true;
      }
      VirtualFile pluginXml = metaInf.createChildData(this, "plugin.xml");
      FileTemplateManager templateManager = FileTemplateManager.getInstance(module.getProject());
      FileTemplate template = templateManager.getJ2eeTemplate("gradleBasedPlugin.xml");
      if (template == null) {
        return false;
      }
      VfsUtil.saveText(pluginXml, template.getText(templateManager.getDefaultProperties()));
      StartupManager.getInstance(module.getProject())
        .runWhenProjectIsInitialized(() -> FileEditorManager.getInstance(module.getProject()).openFile(pluginXml, true));
      return true;
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }
  }

  private static void createRunConfiguration(@NotNull Module module, @NotNull String contentRootPath) {
    RunManager runManager = RunManager.getInstance(module.getProject());
    ConfigurationFactory configurationFactory = new GradleExternalTaskConfigurationType().getConfigurationFactories()[0];
    String configurationName = DevKitBundle.message("run.configuration.title");
    RunnerAndConfigurationSettings configuration = runManager.createRunConfiguration(configurationName, configurationFactory);
    RunConfiguration runConfiguration = configuration.getConfiguration();
    if (runConfiguration instanceof ExternalSystemRunConfiguration) {
      ExternalSystemTaskExecutionSettings settings = ((ExternalSystemRunConfiguration)runConfiguration).getSettings();
      settings.setTaskNames(Collections.singletonList(":runIde"));
      settings.setExternalProjectPath(contentRootPath);
    }
    configuration.setSingleton(true);
    runManager.addConfiguration(configuration, false);
    runManager.setSelectedConfiguration(configuration);
  }
}
