// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve;

import org.jetbrains.annotations.NonNls;

/**
 * @author Vladislav.Soroka
 */
public final class GradleCommonClassNames {
  public static final @NonNls String GRADLE_API_SCRIPT = "org.gradle.api.Script";
  public static final @NonNls String GRADLE_API_PROJECT = "org.gradle.api.Project";
  public static final @NonNls String GRADLE_API_BASE_PLUGIN_CONVENTION = "org.gradle.api.plugins.BasePluginConvention";
  public static final @NonNls String GRADLE_API_JAVA_PLUGIN_CONVENTION = "org.gradle.api.plugins.JavaPluginConvention";
  public static final @NonNls String GRADLE_API_JAVA_PLUGIN_EXTENSION = "org.gradle.api.plugins.internal.DefaultJavaPluginExtension";
  public static final @NonNls String GRADLE_API_APPLICATION_PLUGIN_CONVENTION = "org.gradle.api.plugins.ApplicationPluginConvention";
  public static final @NonNls String GRADLE_API_WAR_CONVENTION = "org.gradle.api.plugins.WarPluginConvention";
  public static final @NonNls String GRADLE_API_CONFIGURATION_CONTAINER = "org.gradle.api.artifacts.ConfigurationContainer";
  public static final @NonNls String GRADLE_API_DEPENDENCY_SUBSTITUTIONS = "org.gradle.api.artifacts.DependencySubstitutions";
  public static final @NonNls String GRADLE_API_RESOLUTION_STRATEGY = "org.gradle.api.artifacts.ResolutionStrategy";
  public static final @NonNls String GRADLE_API_CONFIGURATION = "org.gradle.api.artifacts.Configuration";
  public static final @NonNls String GRADLE_API_ARTIFACT_HANDLER = "org.gradle.api.artifacts.dsl.ArtifactHandler";
  public static final @NonNls String GRADLE_API_PUBLISH_ARTIFACT = "org.gradle.api.artifacts.PublishArtifact";
  public static final @NonNls String GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT = "org.gradle.api.artifacts.ConfigurablePublishArtifact";
  public static final @NonNls String GRADLE_API_PUBLICATION_CONTAINER = "org.gradle.api.publish.PublicationContainer";
  public static final @NonNls String GRADLE_API_DEPENDENCY_HANDLER = "org.gradle.api.artifacts.dsl.DependencyHandler";
  public static final @NonNls String GRADLE_API_COMPONENT_METADATA_HANDLER = "org.gradle.api.artifacts.dsl.ComponentMetadataHandler";
  public static final @NonNls String GRADLE_API_COMPONENT_MODULE_METADATA_HANDLER = "org.gradle.api.artifacts.dsl.ComponentModuleMetadataHandler";
  public static final @NonNls String GRADLE_API_COMPONENT_MODULE_METADATA = "org.gradle.api.artifacts.ComponentModuleMetadata";
  public static final @NonNls String GRADLE_API_COMPONENT_MODULE_METADATA_DETAILS = "org.gradle.api.artifacts.ComponentModuleMetadataDetails";
  public static final @NonNls String GRADLE_API_ACTION = "org.gradle.api.Action";
  public static final @NonNls String GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY = "org.gradle.api.artifacts.ExternalModuleDependency";
  public static final @NonNls String GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY_BUNDLE = "org.gradle.api.artifacts.ExternalModuleDependencyBundle";
  public static final @NonNls String GRADLE_API_ARTIFACTS_PROJECT_DEPENDENCY = "org.gradle.api.artifacts.ProjectDependency";
  public static final @NonNls String GRADLE_API_ARTIFACTS_SELF_RESOLVING_DEPENDENCY = "org.gradle.api.artifacts.SelfResolvingDependency";
  public static final @NonNls String GRADLE_API_ARTIFACTS_CLIENT_MODULE_DEPENDENCY = "org.gradle.api.artifacts.ClientModule";
  public static final @NonNls String GRADLE_API_ARTIFACTS_MODULE_DEPENDENCY = "org.gradle.api.artifacts.ModuleDependency";
  public static final @NonNls String GRADLE_API_ARTIFACTS_DEPENDENCY = "org.gradle.api.artifacts.Dependency";
  public static final @NonNls String GRADLE_API_ARTIFACTS_MINIMAL_EXTERNAL_MODULE_DEPENDENCY = "org.gradle.api.artifacts.MinimalExternalModuleDependency";
  public static final @NonNls String GRADLE_API_ARTIFACTS_DEPENDENCY_ARTIFACT = "org.gradle.api.artifacts.DependencyArtifact";
  public static final @NonNls String GRADLE_API_REPOSITORY_HANDLER = "org.gradle.api.artifacts.dsl.RepositoryHandler";
  public static final @NonNls String GRADLE_API_PUBLISHING_EXTENSION = "org.gradle.api.publish.PublishingExtension";
  public static final @NonNls String GRADLE_API_SOURCE_DIRECTORY_SET = "org.gradle.api.file.SourceDirectorySet";
  public static final @NonNls String GRADLE_API_SOURCE_SET = "org.gradle.api.tasks.SourceSet";
  public static final @NonNls String GRADLE_API_SOURCE_SET_CONTAINER = "org.gradle.api.tasks.SourceSetContainer";
  public static final @NonNls String GRADLE_API_DISTRIBUTION_CONTAINER = "org.gradle.api.distribution.DistributionContainer";
  public static final @NonNls String GRADLE_API_DISTRIBUTION = "org.gradle.api.distribution.Distribution";
  public static final @NonNls String GRADLE_API_FILE_COPY_SPEC = "org.gradle.api.file.CopySpec";
  public static final @NonNls String GRADLE_API_FILE_FILE_COLLECTION = "org.gradle.api.file.FileCollection";
  public static final @NonNls String GRADLE_API_FILE_CONFIGURABLE_FILE_TREE = "org.gradle.api.file.ConfigurableFileTree";
  public static final @NonNls String GRADLE_API_FILE_CONFIGURABLE_FILE_COLLECTION = "org.gradle.api.file.ConfigurableFileCollection";
  public static final @NonNls String GRADLE_API_SCRIPT_HANDLER = "org.gradle.api.initialization.dsl.ScriptHandler";
  public static final @NonNls String GRADLE_API_VERSION_CATALOG_BUILDER = "org.gradle.api.initialization.dsl.VersionCatalogBuilder";
  public static final @NonNls String GRADLE_API_TASK = "org.gradle.api.Task";
  public static final @NonNls String GRADLE_API_DEFAULT_TASK = "org.gradle.api.DefaultTask";
  public static final @NonNls String GRADLE_API_TASKS_ACTION = "org.gradle.api.tasks.TaskAction";
  public static final @NonNls String GRADLE_API_TASKS_DELETE = "org.gradle.api.tasks.Delete";
  public static final @NonNls String GRADLE_JVM_TASKS_JAR = "org.gradle.jvm.tasks.Jar";
  public static final @NonNls String GRADLE_API_TASKS_BUNDLING_JAR = "org.gradle.api.tasks.bundling.Jar";
  public static final @NonNls String GRADLE_API_TASKS_BUNDLING_WAR = "org.gradle.api.tasks.bundling.War";
  public static final @NonNls String GRADLE_API_TASKS_COMPILE_JAVA_COMPILE = "org.gradle.api.tasks.compile.JavaCompile";
  public static final @NonNls String GRADLE_API_TASKS_WRAPPER_WRAPPER = "org.gradle.api.tasks.wrapper.Wrapper";
  public static final @NonNls String GRADLE_API_TASKS_JAVADOC_JAVADOC = "org.gradle.api.tasks.javadoc.Javadoc";
  public static final @NonNls String GRADLE_API_TASKS_DIAGNOSTICS_DEPENDENCY_REPORT_TASK = "org.gradle.api.tasks.diagnostics.DependencyReportTask";
  public static final @NonNls String GRADLE_API_TASKS_DIAGNOSTICS_DEPENDENCY_INSIGHT_REPORT_TASK = "org.gradle.api.tasks.diagnostics.DependencyInsightReportTask";
  public static final @NonNls String GRADLE_API_TASKS_DIAGNOSTICS_PROJECT_REPORT_TASK = "org.gradle.api.tasks.diagnostics.ProjectReportTask";
  public static final @NonNls String GRADLE_API_TASKS_DIAGNOSTICS_PROPERTY_REPORT_TASK = "org.gradle.api.tasks.diagnostics.PropertyReportTask";
  public static final @NonNls String GRADLE_API_TASKS_DIAGNOSTICS_TASK_REPORT_TASK = "org.gradle.api.tasks.diagnostics.TaskReportTask";
  public static final @NonNls String GRADLE_API_TASKS_TESTING_TEST = "org.gradle.api.tasks.testing.Test";
  public static final @NonNls String GRADLE_API_JUNIT_OPTIONS = "org.gradle.api.tasks.testing.junit.JUnitOptions";
  public static final @NonNls String GRADLE_API_TEST_LOGGING_CONTAINER = "org.gradle.api.tasks.testing.logging.TestLoggingContainer";
  public static final @NonNls String GRADLE_API_TASKS_UPLOAD = "org.gradle.api.tasks.Upload";
  public static final @NonNls String GRADLE_LANGUAGE_JVM_TASKS_PROCESS_RESOURCES = "org.gradle.language.jvm.tasks.ProcessResources";
  public static final @NonNls String GRADLE_BUILDSETUP_TASKS_SETUP_BUILD = "org.gradle.buildsetup.tasks.SetupBuild";
  public static final @NonNls String GRADLE_API_TASK_CONTAINER = "org.gradle.api.tasks.TaskContainer";
  public static final @NonNls String GRADLE_API_TASK_COLLECTION = "org.gradle.api.tasks.TaskCollection";
  public static final @NonNls String GRADLE_API_JAVA_ARCHIVES_MANIFEST = "org.gradle.api.java.archives.Manifest";
  public static final @NonNls String GRADLE_API_DOMAIN_OBJECT_COLLECTION = "org.gradle.api.DomainObjectCollection";
  public static final @NonNls String GRADLE_API_NAMED_DOMAIN_OBJECT_COLLECTION = "org.gradle.api.NamedDomainObjectCollection";
  public static final @NonNls String GRADLE_API_NAMED_DOMAIN_OBJECT_CONTAINER = "org.gradle.api.NamedDomainObjectContainer";
  public static final @NonNls String GRADLE_API_ARTIFACTS_REPOSITORIES_ARTIFACT_REPOSITORY = "org.gradle.api.artifacts.repositories.ArtifactRepository";
  public static final @NonNls String GRADLE_API_ARTIFACTS_REPOSITORIES_MAVEN_ARTIFACT_REPOSITORY = "org.gradle.api.artifacts.repositories.MavenArtifactRepository";
  public static final @NonNls String GRADLE_API_ARTIFACTS_REPOSITORIES_IVY_ARTIFACT_REPOSITORY = "org.gradle.api.artifacts.repositories.IvyArtifactRepository";
  public static final @NonNls String GRADLE_API_ARTIFACTS_REPOSITORIES_FLAT_DIRECTORY_ARTIFACT_REPOSITORY = "org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository";
  public static final @NonNls String GRADLE_API_ARTIFACTS_MAVEN_MAVEN_DEPLOYER = "org.gradle.api.artifacts.maven.MavenDeployer";
  public static final @NonNls String GRADLE_API_ARTIFACTS_MUTABLE_VERSION_CONSTRAINT = "org.gradle.api.artifacts.MutableVersionConstraint";
  public static final @NonNls String GRADLE_API_PLUGINS_MAVEN_REPOSITORY_HANDLER_CONVENTION = "org.gradle.api.plugins.MavenRepositoryHandlerConvention";
  public static final @NonNls String GRADLE_API_INITIALIZATION_SETTINGS = "org.gradle.api.initialization.Settings";
  public static final @NonNls String GRADLE_API_EXTRA_PROPERTIES_EXTENSION = "org.gradle.api.plugins.ExtraPropertiesExtension";
  public static final @NonNls String GRADLE_PROCESS_EXEC_SPEC = "org.gradle.process.ExecSpec";
  public static final @NonNls String GRADLE_API_PROVIDER_PROPERTY = "org.gradle.api.provider.Property";
  public static final @NonNls String GRADLE_API_PROVIDER_MAP_PROPERTY = "org.gradle.api.provider.MapProperty";
  public static final @NonNls String GRADLE_API_PROVIDER_HAS_MULTIPLE_VALUES = "org.gradle.api.provider.HasMultipleValues";
  public static final @NonNls String GRADLE_API_PROVIDER_PROVIDER = "org.gradle.api.provider.Provider";
  public static final @NonNls String GRADLE_API_PROVIDER_PROVIDER_CONVERTIBLE = "org.gradle.api.provider.ProviderConvertible";
  public static final @NonNls String GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCY = "org.gradle.plugin.use.PluginDependency";
  public static final @NonNls String GRADLE_PLUGIN_USE_PLUGIN_DEPENDENCIES_SPEC = "org.gradle.plugin.use.PluginDependenciesSpec";
  public static final @NonNls String GRADLE_API_SUPPORTS_KOTLIN_ASSIGNMENT_OVERLOADING = "org.gradle.api.SupportsKotlinAssignmentOverloading";

  private GradleCommonClassNames() {
  }
}
