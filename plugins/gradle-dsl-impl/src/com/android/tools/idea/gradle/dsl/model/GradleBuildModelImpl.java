/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model;

import static com.android.tools.idea.gradle.dsl.GradleUtil.FN_GRADLE_PROPERTIES;
import static com.android.tools.idea.gradle.dsl.GradleUtil.getBaseDirPath;
import static com.android.tools.idea.gradle.dsl.GradleUtil.getGradleBuildFile;
import static com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID;
import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.BUILDSCRIPT;
import static com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement.SUBPROJECTS;
import static com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement.CONFIGURATIONS;
import static com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.DEPENDENCIES;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT;
import static com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement.JAVA;
import static com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement.PLUGINS;
import static com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement.REPOSITORIES;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleFileModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.PluginModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationsModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.java.JavaModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl;
import com.android.tools.idea.gradle.dsl.model.build.BuildScriptModelImpl;
import com.android.tools.idea.gradle.dsl.model.configurations.ConfigurationsModelImpl;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl;
import com.android.tools.idea.gradle.dsl.model.java.JavaModelImpl;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModelImpl;
import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement;
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradlePropertiesFile;
import com.android.tools.idea.gradle.dsl.parser.files.GradleSettingsFile;
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement;
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class GradleBuildModelImpl extends GradleFileModelImpl implements GradleBuildModel {
  @NonNls private static final String PLUGIN = "plugin";
  @NonNls private static final String ID = "id";

  /**
   * @deprecated Use {@link ProjectBuildModel#get(Project)} instead.
   */
  @Deprecated
  @Nullable
  public static GradleBuildModel get(@NotNull Project project) {
    VirtualFile file = getGradleBuildFile(getBaseDirPath(project));
    return file != null ? parseBuildFile(file, project, project.getName()) : null;
  }

  /**
   * @deprecated Use {@link ProjectBuildModel#getModuleBuildModel(Module)} instead.
   */
  @Deprecated
  @Nullable
  public static GradleBuildModel get(@NotNull Module module) {
    VirtualFile file = getGradleBuildFile(module);
    return file != null ? parseBuildFile(file, module.getProject(), module.getName()) : null;
  }

  /**
   * @deprecated Use {@link ProjectBuildModel#getModuleBuildModel(Module)} instead.
   */
  @Deprecated
  @NotNull
  public static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    return parseBuildFile(file, project, "<Unknown>");
  }

  /**
   * @deprecated Use {@link ProjectBuildModel#getModuleBuildModel(Module)} instead.
   */
  @Deprecated
  @NotNull
  public static GradleBuildModel parseBuildFile(@NotNull VirtualFile file,
                                                @NotNull Project project,
                                                @NotNull String moduleName) {
    return new GradleBuildModelImpl(BuildModelContext.create(project).getOrCreateBuildFile(file, moduleName, false));
  }

  @Deprecated
  @NotNull
  @Override
  public List<GradleNotNullValue<String>> appliedPlugins() {
    return plugins().stream().map(plugin -> new GradleNotNullValue<String>() {
      @NotNull
      @Override
      public VirtualFile getFile() {
        return plugin.name().getGradleFile();
      }

      @NotNull
      @Override
      public String getPropertyName() {
        return plugin.name().getName();
      }

      @Nullable
      @Override
      public String getDslText() {
        return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
          PsiElement e = plugin.name().getPsiElement();
          return e == null ? null : e.getText();
        });
      }

      @NotNull
      @Override
      public Map<String, GradleNotNullValue<Object>> getResolvedVariables() {
        return ImmutableMap.of();
      }

      @NotNull
      @Override
      public String value() {
        return plugin.name().forceString();
      }

      @Nullable
      @Override
      public PsiElement getPsiElement() {
        return plugin.name().getPsiElement();
      }
    }).collect(Collectors.toList());
  }

  /**
   *  Parses a build file and produces the {@link GradleBuildFile} that represents it.
   *
   * @param file the build file that should be parsed, this must be a gradle build file
   * @param project the project that the build file belongs to
   * @param moduleName the name of the module
   * @param context the context that should be used for this parse
   * @param isApplied whether or not the file should be parsed as if it was applied, if true we do not populate the
   *                  file with the properties found in the subprojects block. This should be true for any file that is not part of the
   *                  main build.gradle structure (i.e project and module files) otherwise we might attempt to parse the file we are parsing
   *                  again leading to a stack overflow.
   * @return the model of the given Gradle file.
   */
  @NotNull
  public static GradleBuildFile parseBuildFile(@NotNull VirtualFile file,
                                               @NotNull Project project,
                                               @NotNull String moduleName,
                                               @NotNull BuildModelContext context,
                                               boolean isApplied) {
    GradleBuildFile buildDslFile = new GradleBuildFile(file, project, moduleName, context);
    ApplicationManager.getApplication().runReadAction(() -> {
      if (!isApplied) {
        populateWithParentModuleSubProjectsProperties(buildDslFile, context);
      }
      populateSiblingDslFileWithGradlePropertiesFile(buildDslFile, context);
      buildDslFile.parse();
    });
    return buildDslFile;
  }

  public static void populateWithParentModuleSubProjectsProperties(@NotNull GradleBuildFile buildDslFile,
                                                                   @NotNull BuildModelContext context) {
    VirtualFile maybeSettingsFile = buildDslFile.tryToFindSettingsFile();
    if (maybeSettingsFile == null) {
      return;
    }
    GradleSettingsFile settingsFile = context.getOrCreateSettingsFile(maybeSettingsFile);

    GradleSettingsModel gradleSettingsModel = new GradleSettingsModelImpl(settingsFile);
    String modulePath = gradleSettingsModel.moduleWithDirectory(buildDslFile.getDirectoryPath());
    if (modulePath == null) {
      return;
    }

    GradleBuildModel parentModuleModel = gradleSettingsModel.getParentModuleModel(modulePath);
    if (!(parentModuleModel instanceof GradleBuildModelImpl)) {
      return;
    }

    GradleBuildModelImpl parentModuleModelImpl = (GradleBuildModelImpl)parentModuleModel;

    GradleDslFile parentModuleDslFile = parentModuleModelImpl.myGradleDslFile;
    buildDslFile.setParentModuleDslFile(parentModuleDslFile);

    SubProjectsDslElement subProjectsDslElement = parentModuleDslFile.getPropertyElement(SUBPROJECTS);
    if (subProjectsDslElement == null) {
      return;
    }

    buildDslFile.setParsedElement(subProjectsDslElement);
    for (Map.Entry<String, GradleDslElement> entry : subProjectsDslElement.getPropertyElements().entrySet()) {
      GradleDslElement element = entry.getValue();
      // TODO(b/147139838): we need to implement a sufficiently-deep copy to handle subprojects correctly: as it stands, this special-case
      //  shallow copy works around a particular bug, but e.g. configuring some android properties in a subprojects block, then
      //  modifying the configuration in one subproject will cause the parser to propagate that modification to all other subprojects
      //  because of the shared structure.  (The copy must not be too deep: we probably want to preserve the association of properties
      //  to the actual file they're defined in).
      if (element instanceof ApplyDslElement) {
        ApplyDslElement subProjectsApply = (ApplyDslElement)element;
        ApplyDslElement myApply = new ApplyDslElement(buildDslFile);
        buildDslFile.setParsedElement(myApply);
        for (GradleDslElement appliedElement : subProjectsApply.getAllElements()) {
          myApply.addParsedElement(appliedElement);
        }
      }
      else {
        // TODO(b/147139838): I believe this is wrong in general (see comment above, and the referenced bug, for details)
        buildDslFile.setParsedElement(element);
      }
    }
  }

  public static void populateSiblingDslFileWithGradlePropertiesFile(@NotNull GradleBuildFile buildDslFile,
                                                                    @NotNull BuildModelContext context) {
    File propertiesFilePath = new File(buildDslFile.getDirectoryPath(), FN_GRADLE_PROPERTIES);
    VirtualFile propertiesFile = findFileByIoFile(propertiesFilePath, false);
    if (propertiesFile == null) {
      return;
    }

    GradlePropertiesFile parsedProperties = context.getOrCreatePropertiesFile(propertiesFile, buildDslFile.getName());
    if (parsedProperties == null) {
      return;
    }
    GradlePropertiesModel propertiesModel = new GradlePropertiesModel(parsedProperties);

    GradleDslFile propertiesDslFile = propertiesModel.myGradleDslFile;
    buildDslFile.setSiblingDslFile(propertiesDslFile);
    propertiesDslFile.setSiblingDslFile(buildDslFile);
  }

  GradleBuildModelImpl(@NotNull GradleBuildFile buildDslFile) {
    super(buildDslFile);
  }

  @Override
  @NotNull
  public List<PluginModel> plugins() {
    // Look for plugins block first if it exists, and then look for apply block to retrieve plugins.
    PluginsDslElement pluginsDslElement = myGradleDslFile.getPropertyElement(PLUGINS);
    ArrayList<PluginModelImpl> plugins = new ArrayList<>();
    if (pluginsDslElement != null) {
      plugins.addAll(PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(pluginsDslElement)).values());
    }

    ApplyDslElement applyDslElement = myGradleDslFile.getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
    if (applyDslElement != null) {
      plugins.addAll(PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(applyDslElement)).values());
    }

    return new ArrayList<>(PluginModelImpl.deduplicatePlugins(plugins).values());
  }

  @NotNull
  @Override
  public PluginModel applyPlugin(@NotNull String plugin) {
    PluginsDslElement pluginsDslElement = myGradleDslFile.getPropertyElement(PLUGINS);
    ApplyDslElement applyDslElement = myGradleDslFile.getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
    // If no plugins declaration exist, create a PluginDslElement to apply plugins
    if (pluginsDslElement == null && applyDslElement == null) {
      pluginsDslElement = new PluginsDslElement(myGradleDslFile, GradleNameElement.fake(PLUGINS.name));
      myGradleDslFile.addNewElementAt(0, pluginsDslElement);
    }
    else if (pluginsDslElement == null) {
      Map<String, PluginModelImpl> models = PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(applyDslElement));
      if (models.containsKey(plugin)) {
        return models.get(plugin);
      }

      GradleDslExpressionMap applyMap = new GradleDslExpressionMap(myGradleDslFile, GradleNameElement.create(APPLY_BLOCK_NAME));
      applyMap.setAsNamedArgs(true);
      GradleDslLiteral literal = new GradleDslLiteral(applyMap, GradleNameElement.create(PLUGIN));
      literal.setValue(plugin.trim());
      applyMap.setNewElement(literal);
      applyDslElement.setNewElement(applyMap);

      return new PluginModelImpl(applyMap, literal);
    }

    Map<String, PluginModelImpl> models = PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(pluginsDslElement));
    if (models.containsKey(plugin)) {
      return models.get(plugin);
    }
    if (applyDslElement != null) {
      Map<String, PluginModelImpl> applyPluginsModels = PluginModelImpl.deduplicatePlugins(PluginModelImpl.create(applyDslElement));
      if (applyPluginsModels.containsKey(plugin)) {
        return applyPluginsModels.get(plugin);
      }
    }

    // Create the plugin literal.
    GradleDslLiteral literal = new GradleDslLiteral(pluginsDslElement, GradleNameElement.create(ID));
    literal.setElementType(PropertyType.REGULAR);
    literal.setValue(plugin.trim());
    pluginsDslElement.setNewElement(literal);

    return new PluginModelImpl(literal, literal);
  }

  @Override
  public void removePlugin(@NotNull String plugin) {
    // First look for plugins{} block (i.e. PluginsDslElement) if it exists, and try to remove the plugin from.
    PluginsDslElement pluginsDslElement = myGradleDslFile.getPropertyElement(PLUGINS);
    if (pluginsDslElement != null) {
      PluginModelImpl.removePlugins(PluginModelImpl.create(pluginsDslElement), plugin);
    }

    // If ApplyDslElement exists, try to remove the plugin from it as well (We might have plugins defined in both apply and plugins blocks).
    ApplyDslElement applyDslElement = myGradleDslFile.getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
    if (applyDslElement != null) {
      PluginModelImpl.removePlugins(PluginModelImpl.create(applyDslElement), plugin);
    }

  }

  /**
   * Returns {@link AndroidModelImpl} to read and update android block contents in the build.gradle file.
   *
   * <p>Returns {@code null} when experimental plugin is used as reading and updating android section is not supported for the
   * experimental dsl.</p>
   */
  @Override
  @NotNull
  public AndroidModel android() {
    AndroidDslElement androidDslElement = myGradleDslFile.ensurePropertyElement(ANDROID);
    return new AndroidModelImpl(androidDslElement);
  }

  @NotNull
  @Override
  public BuildScriptModel buildscript() {
    BuildScriptDslElement buildScriptDslElement = myGradleDslFile.ensurePropertyElement(BUILDSCRIPT);
    return new BuildScriptModelImpl(buildScriptDslElement);
  }

  @NotNull
  @Override
  public ConfigurationsModel configurations() {
    ConfigurationsDslElement configurationsDslElement =
      myGradleDslFile.ensurePropertyElementBefore(CONFIGURATIONS, DependenciesDslElement.class);
    return new ConfigurationsModelImpl(configurationsDslElement);
  }

  @NotNull
  @Override
  public DependenciesModel dependencies() {
    DependenciesDslElement dependenciesDslElement = myGradleDslFile.ensurePropertyElement(DEPENDENCIES);
    return new DependenciesModelImpl(dependenciesDslElement);
  }

  @Override
  @NotNull
  public ExtModel ext() {
    int at = 0;
    List<GradleDslElement> elements = myGradleDslFile.getAllElements();
    if (!elements.isEmpty() && elements.get(0) instanceof ApplyDslElement) {
      at += 1;
    }
    ExtDslElement extDslElement = myGradleDslFile.ensurePropertyElementAt(EXT, at);
    return new ExtModelImpl(extDslElement);
  }

  @NotNull
  @Override
  public JavaModel java() {
    JavaDslElement javaDslElement = myGradleDslFile.ensurePropertyElement(JAVA);
    return new JavaModelImpl(javaDslElement);
  }

  @NotNull
  @Override
  public RepositoriesModel repositories() {
    RepositoriesDslElement repositoriesDslElement = myGradleDslFile.ensurePropertyElement(REPOSITORIES);
    return new RepositoriesModelImpl(repositoriesDslElement);
  }

  @Override
  @NotNull
  public Set<GradleFileModel> getInvolvedFiles() {
    return getAllInvolvedFiles().stream().distinct().map(e -> getFileModel(e)).collect(Collectors.toSet());
  }

  @NotNull
  private static GradleFileModel getFileModel(@NotNull GradleDslFile file) {
    if (file instanceof GradleBuildFile) {
      return new GradleBuildModelImpl((GradleBuildFile)file);
    }
    else if (file instanceof GradleSettingsFile) {
      return new GradleSettingsModelImpl((GradleSettingsFile)file);
    }
    else if (file instanceof GradlePropertiesFile) {
      return new GradlePropertiesModel(file);
    }
    throw new IllegalStateException("Unknown GradleDslFile type found!");
  }

  /**
   * Removes property {@link RepositoriesDslElement#REPOSITORIES}.
   */
  @Override
  @TestOnly
  public void removeRepositoriesBlocks() {
    myGradleDslFile.removeProperty(REPOSITORIES.name);
  }
}
