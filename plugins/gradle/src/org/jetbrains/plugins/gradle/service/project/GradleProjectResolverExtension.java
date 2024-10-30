// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.ParametersEnhancer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import org.gradle.tooling.model.build.BuildEnvironment;
import org.gradle.tooling.model.idea.IdeaModule;
import org.gradle.tooling.model.idea.IdeaProject;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.GradleManager;
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider;

import java.util.*;

/**
 * Allows to enhance {@link GradleProjectResolver} processing.
 * <p/>
 * Every extension is expected to have a no-args constructor because they are used at external process and we need a simple way
 * to instantiate it.
 *
 * @author Vladislav Soroka
 * @see GradleManager#enhanceRemoteProcessing(SimpleJavaParameters)   sample enhanceParameters() implementation
 */
public interface GradleProjectResolverExtension extends ParametersEnhancer {

  @ApiStatus.Internal
  ExtensionPointName<GradleProjectResolverExtension> EP_NAME = ExtensionPointName.create("org.jetbrains.plugins.gradle.projectResolve");

  void setProjectResolverContext(@NotNull ProjectResolverContext projectResolverContext);

  void setNext(@NotNull GradleProjectResolverExtension projectResolverExtension);

  @Nullable
  GradleProjectResolverExtension getNext();

  void populateProjectExtraModels(@NotNull IdeaProject gradleProject, @NotNull DataNode<ProjectData> ideProject);

  @Nullable
  DataNode<ModuleData> createModule(@NotNull IdeaModule gradleModule, @NotNull DataNode<ProjectData> projectDataNode);

  /**
   * Populates extra models of the given ide module on the basis of the information provided by {@link org.jetbrains.plugins.gradle.tooling.ModelBuilderService}
   *
   * @param ideModule corresponding module from intellij gradle plugin domain
   */
  void populateModuleExtraModels(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule);

  /**
   * Populates {@link com.intellij.openapi.externalSystem.model.ProjectKeys#CONTENT_ROOT) content roots} of the given ide module on the basis of the information
   * contained at the given gradle module.
   *
   * @param gradleModule holder of the module information received from the gradle tooling api
   * @param ideModule    corresponding module from intellij gradle plugin domain
   * @throws IllegalArgumentException if given gradle module contains invalid data
   */
  void populateModuleContentRoots(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule);

  void populateModuleCompileOutputSettings(@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule);

  void populateModuleDependencies(@NotNull IdeaModule gradleModule,
                                  @NotNull DataNode<ModuleData> ideModule,
                                  @NotNull DataNode<ProjectData> ideProject);

  @NotNull
  Collection<TaskData> populateModuleTasks(@NotNull IdeaModule gradleModule,
                                           @NotNull DataNode<ModuleData> ideModule,
                                           @NotNull DataNode<ProjectData> ideProject);

  /**
   * Called when the project data has been obtained and resolved
   * @param projectDataNode project data graph
   */
  @ApiStatus.Experimental
  default void resolveFinished(@NotNull DataNode<ProjectData> projectDataNode) {}

  @NotNull
  Set<Class<?>> getExtraProjectModelClasses();

  @NotNull
  Set<Class<?>> getExtraBuildModelClasses();

  @Nullable
  default ProjectImportModelProvider getModelProvider() { return null; }

  @NotNull
  default List<ProjectImportModelProvider> getModelProviders() {
    ProjectImportModelProvider provider = getModelProvider();
    return provider == null ? Collections.emptyList() : List.of(provider);
  }

  /**
   * add paths containing these classes to classpath of gradle tooling extension
   *
   * @return classes to be available for gradle
   */
  @NotNull
  Set<Class<?>> getToolingExtensionsClasses();

  /**
   * add target types to be used in the polymorphic containers
   */
  default Set<Class<?>> getTargetTypes() {
    return Collections.emptySet();
  }

  @NotNull
  List<Pair<String, String>> getExtraJvmArgs();

  @NotNull
  List<String> getExtraCommandLineArgs();

  @NotNull
  ExternalSystemException getUserFriendlyError(@Nullable BuildEnvironment buildEnvironment,
                                               @NotNull Throwable error,
                                               @NotNull String projectPath,
                                               @Nullable String buildFilePath);

  /**
   * Performs project configuration and other checks before the actual project import (before invocation of gradle tooling API).
   */
  void preImportCheck();

  /**
   * Allows extension to contribute to init script
   * @param taskNames gradle task names to be executed
   * @param jvmParametersSetup jvm configuration that will be applied to Gradle jvm
   * @param initScriptConsumer consumer of init script text. Must be called to add script txt
   */
  void enhanceTaskProcessing(
    @NotNull List<String> taskNames,
    @Nullable String jvmParametersSetup,
    @NotNull Consumer<String> initScriptConsumer
  );

  // jvm configuration that will be applied to Gradle jvm
  String JVM_PARAMETERS_SETUP_KEY = "JVM_PARAMETERS_SETUP";

  // flag that shows if tasks will be treated as tests invocation by the IDE (e.g., test events are expected)
  String IS_RUN_AS_TEST_KEY = "IS_RUN_AS_TEST";

  // Test events will be produces by TAPI and there is no need for console reporting
  String IS_BUILT_IN_TEST_EVENTS_USED_KEY = "IS_BUILT_IN_TEST_EVENTS_USED";

  // port for callbacks which Gradle tasks communicate to IDE
  String DEBUG_DISPATCH_PORT_KEY = "DEBUG_DISPATCH_PORT";

  // address for callbacks which Gradle tasks communicate to IDE
  String DEBUG_DISPATCH_ADDR_KEY = "DEBUG_DISPATCH_ADDR";

  // options passed from project to Gradle
  String DEBUG_OPTIONS_KEY = "DEBUG_OPTIONS";

  // for Gradle version specific init scripts
  String GRADLE_VERSION = "GRADLE_VERSION";

  // debug flag that will always be passed at runtime if debugging is enabled
  String DEBUGGER_ENABLED = "DEBUGGER_ENABLED";

  /**
   * Allows an extension to contribute an init script that would be passed into the Gradle execution
   *
   * @param project            project (if available)
   * @param taskNames          gradle task names to be executed
   * @param initScriptConsumer consumer of init script text. Must be called to add script txt
   * @param parameters         storage for passing optional named parameters
   * @return map with environment variables to be passed into the execution
   */
  @ApiStatus.Experimental
  default @NotNull Map<String, String> enhanceTaskProcessing(
    @Nullable Project project,
    @NotNull List<String> taskNames,
    @NotNull Consumer<String> initScriptConsumer,
    @NotNull Map<String, String> parameters
  ) {
    String jvmParametersSetup = parameters.get(JVM_PARAMETERS_SETUP_KEY);
    enhanceTaskProcessing(taskNames, jvmParametersSetup, initScriptConsumer);
    return Map.of();
  }
}
