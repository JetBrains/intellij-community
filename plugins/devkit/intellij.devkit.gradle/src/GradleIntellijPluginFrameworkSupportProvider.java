// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.project.ProjectId;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.plugins.gradle.frameworkSupport.BuildScriptDataBuilder;
import org.jetbrains.plugins.gradle.frameworkSupport.KotlinDslGradleFrameworkSupportProvider;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;

import javax.swing.*;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleIntellijPluginFrameworkSupportProvider extends KotlinDslGradleFrameworkSupportProvider {
  private static final @NonNls String ID = "gradle-intellij-plugin";
  private static final Logger LOG = Logger.getInstance(GradleIntellijPluginFrameworkSupportProvider.class);

  private static final @NonNls String LATEST_GRADLE_VERSION_KEY = "LATEST_GRADLE_VERSION_KEY";
  private static final @NonNls String LATEST_UPDATING_TIME_KEY = "LATEST_UPDATING_TIME_KEY";

  private static final @NonNls String FALLBACK_VERSION = "1.0";
  protected static final @NonNls String HELP_COMMENT = "// See https://github.com/JetBrains/gradle-intellij-plugin/\n";

  private static final @NonNls String TASK_NAME_RUN_IDE = ":runIde";
  private static final @NonNls String META_INF_PATH = "/src/main/resources/META-INF";

  private static final class Lazy {
    static final ExecutorService EXECUTOR = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("UPDATE_GRADLE_PLUGIN_VERSIONS");
  }

  @Override
  public @NotNull FrameworkTypeEx getFrameworkType() {
    return new FrameworkTypeEx(ID) {
      @Override
      public @NotNull FrameworkSupportInModuleProvider createProvider() {
        return GradleIntellijPluginFrameworkSupportProvider.this;
      }

      @Override
      public @NotNull String getPresentableName() {
        return DevKitGradleBundle.message("module.wizard.gradle.presentable.name");
      }

      @Override
      public @NotNull Icon getIcon() {
        return IconManager.getInstance().getPlatformIcon(PlatformIcons.Plugin);
      }
    };
  }

  @Override
  public void addSupport(@NotNull ProjectId projectId,
                         @NotNull Module module,
                         @NotNull ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider,
                         @NotNull BuildScriptDataBuilder buildScriptData) {
    String pluginVersion = PropertiesComponent.getInstance().getValue(LATEST_GRADLE_VERSION_KEY, FALLBACK_VERSION);
    ApplicationInfoEx applicationInfo = ApplicationInfoEx.getInstanceEx();
    String ideVersion;
    if (applicationInfo.isEAP()) {
      BuildNumber build = applicationInfo.getBuild();
      ideVersion = build.isSnapshot() ? build.getBaselineVersion() + "-SNAPSHOT" : build.asStringWithoutProductCode();
    }
    else {
      ideVersion = applicationInfo.getFullVersion();
    }
    configureBuildScript(buildScriptData, pluginVersion, ideVersion);
    VirtualFile contentRoot = ArrayUtil.getFirstElement(rootModel.getContentRoots());
    if (contentRoot == null) return;
    if (!createPluginXml(projectId, module, contentRoot.getPath())) return;
    StartupManager.getInstance(module.getProject()).runWhenProjectIsInitialized(() -> {
      FileEditorManager.getInstance(module.getProject()).openFile(buildScriptData.getBuildScriptFile(), true);
      createRunConfiguration(module, contentRoot.getPath());
    });
  }

  protected void configureBuildScript(@NotNull BuildScriptDataBuilder buildScriptData,
                                      @NonNls String pluginVersion,
                                      @NonNls String ideVersion) {
    buildScriptData
      .addPluginDefinitionInPluginsGroup("id 'org.jetbrains.intellij' version '" + pluginVersion + "'")
      .addOther(HELP_COMMENT +
                "intellij {\n    version = '" + ideVersion + "'\n}\n")
      .addOther("""
                  patchPluginXml {
                      changeNotes = ""\"
                        Add change notes here.<br>
                        <em>most HTML tags may be used</em>""\"
                  }""");
  }

  @Override
  public JComponent createComponent() {
    // checking new gradle version on creating component
    String latestVersion = PropertiesComponent.getInstance().getValue(LATEST_GRADLE_VERSION_KEY);
    long timeCheck = PropertiesComponent.getInstance().getLong(LATEST_UPDATING_TIME_KEY, System.currentTimeMillis());
    if (latestVersion == null || TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - timeCheck) >= 1) {
      ModalityState modalityState = ModalityState.defaultModalityState();
      Lazy.EXECUTOR.execute(() -> {
        try {
          // sadly plugins.gradle.org has no API and doesn't support meta-versions like latest.
          // Let's parse HTML with REGEXPs
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

    final HyperlinkLabel linkLabel = new HyperlinkLabel();
    linkLabel.setHtmlText(DevKitGradleBundle.message("module.wizard.gradle.learn.title"));
    linkLabel.setHyperlinkTarget("https://plugins.jetbrains.com/docs/intellij/developing-plugins.html?from=DevkitGradleProjectWizard");
    return linkLabel;
  }

  private boolean createPluginXml(@NotNull ProjectId projectId, @NotNull Module module, @NotNull String contentRootPath) {
    try {
      VirtualFile metaInf = VfsUtil.createDirectoryIfMissing(contentRootPath + META_INF_PATH);
      if (metaInf == null) {
        return false;
      }
      if (metaInf.findChild(PluginManagerCore.PLUGIN_XML) != null) {
        return true;
      }
      Project project = module.getProject();
      VirtualFile pluginXml = metaInf.createChildData(this, PluginManagerCore.PLUGIN_XML);
      FileTemplateManager templateManager = FileTemplateManager.getInstance(project);
      FileTemplate template = templateManager.getJ2eeTemplate("gradleBasedPlugin.xml");

      @NonNls Map<String, String> attributes = new HashMap<>();
      String groupId = projectId.getGroupId();
      String artifactId = projectId.getArtifactId();
      if (StringUtil.isNotEmpty(artifactId)) {
        attributes.put("PLUGIN_ID", StringUtil.isNotEmpty(groupId) ? groupId + "." + artifactId : artifactId);
      }
      else {
        attributes.put("PLUGIN_ID", project.getName());
      }

      VfsUtil.saveText(pluginXml, template.getText(attributes));
      StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> {
        FileEditorManager.getInstance(project).openFile(pluginXml, true);
      });
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
    RunnerAndConfigurationSettings configuration = runManager.createConfiguration(configurationName, configurationFactory);
    RunConfiguration runConfiguration = configuration.getConfiguration();
    if (runConfiguration instanceof ExternalSystemRunConfiguration) {
      ExternalSystemTaskExecutionSettings settings = ((ExternalSystemRunConfiguration)runConfiguration).getSettings();
      settings.setTaskNames(Collections.singletonList(TASK_NAME_RUN_IDE));
      settings.setExternalProjectPath(contentRootPath);
    }
    runManager.addConfiguration(configuration);
    runManager.setSelectedConfiguration(configuration);
  }
}
