// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.builder

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.RegularFile
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.ManifestInternal
import org.gradle.plugins.ear.Ear
import org.gradle.plugins.ear.EarPlugin
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarConfigurationImpl
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarModelImpl
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarResourceImpl
import org.jetbrains.plugins.gradle.tooling.util.DependencyResolver
import org.jetbrains.plugins.gradle.tooling.util.SourceSetCachedFinder
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl

import static org.jetbrains.plugins.gradle.tooling.internal.ExtraModelBuilder.reportModelBuilderFailure
import static org.jetbrains.plugins.gradle.tooling.util.ReflectionUtil.reflectiveCall
import static org.jetbrains.plugins.gradle.tooling.util.ReflectionUtil.reflectiveGetProperty

/**
 * @author Vladislav.Soroka
 */
@CompileStatic
class EarModelBuilderImpl extends AbstractModelBuilderService {

  private static final String APP_DIR_PROPERTY = "appDirName"
  private SourceSetCachedFinder mySourceSetFinder = null
  // Manifest.writeTo(Writer) was deprecated since 2.14.1 version
  // https://github.com/gradle/gradle/commit/b435112d1baba787fbe4a9a6833401e837df9246
  private static boolean is2_14_1_OrBetter = GradleVersion.current().baseVersion >= GradleVersion.version("2.14.1")
  private static is6OrBetter = GradleVersion.current().baseVersion >= GradleVersion.version("6.0")

  @Override
  boolean canBuild(String modelName) {
    return EarConfiguration.name == modelName
  }

  @Nullable
  @Override
  Object buildAll(String modelName, Project project, @NotNull ModelBuilderContext context) {
    // https://issues.apache.org/jira/browse/GROOVY-9555
    final earPlugin = project.plugins.findPlugin(EarPlugin)
    if (earPlugin == null) return null

    if (mySourceSetFinder == null) mySourceSetFinder = new SourceSetCachedFinder(context)

    final String appDirName = !project.hasProperty(APP_DIR_PROPERTY) ?
                              "src/main/application" : String.valueOf(project.property(APP_DIR_PROPERTY))
    List<? extends EarConfiguration.EarModel> earModels = []

    def deployConfiguration = project.configurations.findByName(EarPlugin.DEPLOY_CONFIGURATION_NAME)
    def earlibConfiguration = project.configurations.findByName(EarPlugin.EARLIB_CONFIGURATION_NAME)

    DependencyResolver dependencyResolver = new DependencyResolverImpl(project, false, false, mySourceSetFinder)

    def deployDependencies = dependencyResolver.resolveDependencies(deployConfiguration)
    def earlibDependencies = dependencyResolver.resolveDependencies(earlibConfiguration)
    def buildDirPath = project.getBuildDir().absolutePath

    for (task in project.tasks) {
      if (task instanceof Ear) {
        final EarModelImpl earModel =
          is6OrBetter ? new EarModelImpl(reflectiveGetProperty(task, "getArchiveFileName", String), appDirName, task.getLibDirName()) :
          new EarModelImpl(reflectiveCall(task, "getArchiveName", String), appDirName, task.getLibDirName())

        final List<EarConfiguration.EarResource> earResources = []
        final Ear earTask = task as Ear

        try {
          new CopySpecWalker().walk(earTask.rootSpec, new CopySpecWalker.Visitor() {
            @Override
            void visitSourcePath(String relativePath, String path) {
              def file = new File(path)
              addPath(buildDirPath, earResources, relativePath, "",
                      file.absolute ? file : new File(earTask.project.projectDir, path),
                      deployConfiguration, earlibConfiguration)
            }

            @Override
            void visitDir(String relativePath, FileVisitDetails dirDetails) {
              addPath(buildDirPath, earResources, relativePath, dirDetails.path, dirDetails.file, deployConfiguration, earlibConfiguration)
            }

            @Override
            void visitFile(String relativePath, FileVisitDetails fileDetails) {
              addPath(buildDirPath, earResources, relativePath, fileDetails.path,
                      fileDetails.file, deployConfiguration, earlibConfiguration)
            }
          })
        }
        catch (Exception e) {
          reportModelBuilderFailure(project, this, context, e)
        }

        earModel.resources = earResources

        def deploymentDescriptor = earTask.deploymentDescriptor
        if (deploymentDescriptor != null) {
          def writer = new StringWriter()
          deploymentDescriptor.writeTo(writer)
          earModel.deploymentDescriptor = writer.toString()
        }

        earModel.archivePath = is6OrBetter ? reflectiveGetProperty(earTask, "getArchiveFile", RegularFile).asFile : earTask.archivePath

        Manifest manifest = earTask.manifest
        if (manifest != null) {
          if (is2_14_1_OrBetter) {
            if (manifest instanceof ManifestInternal) {
              OutputStream outputStream = new ByteArrayOutputStream()
              writeToOutputStream(manifest, outputStream)
              def contentCharset = (manifest as ManifestInternal).contentCharset
              earModel.manifestContent = outputStream.toString(contentCharset)
            }
          }
          else {
            Writer writer = new StringWriter()
            writeToWriter(manifest, writer)
            earModel.manifestContent = writer.toString()
          }
        }

        earModels.add(earModel)
      }
    }

    new EarConfigurationImpl(earModels, deployDependencies, earlibDependencies)
  }

  @NotNull
  @Override
  ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    ErrorMessageBuilder.create(
      project, e, "JEE project import errors"
    ).withDescription("Ear Artifacts may not be configured properly")
  }

  @CompileDynamic
  private static Manifest writeToOutputStream(Manifest manifest, OutputStream outputStream) {
    return manifest.writeTo(outputStream)
  }

  @CompileDynamic
  private static Manifest writeToWriter(Manifest manifest, StringWriter writer) {
    return manifest.writeTo((Writer)writer)
  }

  private static void addPath(String buildDirPath,
                                 List<EarConfiguration.EarResource> earResources,
                                 String earRelativePath,
                                 String fileRelativePath,
                                 File file,
                                 Configuration... earConfigurations) {

    if (file.absolutePath.startsWith(buildDirPath)) return

    for (Configuration conf : earConfigurations) {
      if (conf.files.contains(file)) return
    }

    earRelativePath = earRelativePath == null ? "" : earRelativePath

    EarConfiguration.EarResource earResource = new EarResourceImpl(earRelativePath, fileRelativePath, file)
    earResources.add(earResource)
  }
}
