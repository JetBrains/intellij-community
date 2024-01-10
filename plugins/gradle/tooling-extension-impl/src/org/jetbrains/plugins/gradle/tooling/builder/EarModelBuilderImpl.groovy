// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.builder

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages
import com.intellij.gradle.toolingExtension.impl.util.GradleProjectUtil
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.internal.ManifestInternal
import org.gradle.plugins.ear.Ear
import org.gradle.plugins.ear.EarPlugin
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.model.ear.EarConfiguration
import org.jetbrains.plugins.gradle.tooling.AbstractModelBuilderService
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarConfigurationImpl
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarModelImpl
import org.jetbrains.plugins.gradle.tooling.internal.ear.EarResourceImpl
import org.jetbrains.plugins.gradle.tooling.util.DependencyResolver
import org.jetbrains.plugins.gradle.tooling.util.resolve.DependencyResolverImpl

import static com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil.getTaskArchiveFile
import static com.intellij.gradle.toolingExtension.impl.util.GradleTaskUtil.getTaskArchiveFileName
import static com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.reflectiveCall
import static com.intellij.gradle.toolingExtension.util.GradleReflectionUtil.reflectiveGetProperty

/**
 * @author Vladislav.Soroka
 */
@CompileStatic
class EarModelBuilderImpl extends AbstractModelBuilderService {

  private static final String APP_DIR_PROPERTY = "appDirName"
  private static final boolean is82OrBetter = GradleVersionUtil.isCurrentGradleAtLeast("8.2")

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

    List<? extends EarConfiguration.EarModel> earModels = []

    def deployConfiguration = project.configurations.findByName(EarPlugin.DEPLOY_CONFIGURATION_NAME)
    def earlibConfiguration = project.configurations.findByName(EarPlugin.EARLIB_CONFIGURATION_NAME)

    DependencyResolver dependencyResolver = new DependencyResolverImpl(context, project, false, false)

    def deployDependencies = dependencyResolver.resolveDependencies(deployConfiguration)
    def earlibDependencies = dependencyResolver.resolveDependencies(earlibConfiguration)
    def buildDirPath = GradleProjectUtil.getBuildDirectory(project).absolutePath

    for (task in project.tasks) {
      if (task instanceof Ear) {

        String appDirName
        if (is82OrBetter) {
          def appDirectoryLocation = reflectiveGetProperty(task, "getAppDirectory", Object)
          appDirName = reflectiveCall(appDirectoryLocation, "getAsFile", File).absolutePath
        } else {
          appDirName = !project.hasProperty(APP_DIR_PROPERTY) ?
                       "src/main/application" : String.valueOf(project.property(APP_DIR_PROPERTY))
        }

        final EarModelImpl earModel = new EarModelImpl(getTaskArchiveFileName(task), appDirName, task.getLibDirName())

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
          reportErrorMessage(modelName, project, context, e)
        }

        earModel.resources = earResources

        def deploymentDescriptor = earTask.deploymentDescriptor
        if (deploymentDescriptor != null) {
          def writer = new StringWriter()
          deploymentDescriptor.writeTo(writer)
          earModel.deploymentDescriptor = writer.toString()
        }

        earModel.archivePath = getTaskArchiveFile(earTask)

        Manifest manifest = earTask.manifest
        if (manifest instanceof ManifestInternal) {
          OutputStream outputStream = new ByteArrayOutputStream()
          writeToOutputStream(manifest, outputStream)
          def contentCharset = (manifest as ManifestInternal).contentCharset
          earModel.manifestContent = outputStream.toString(contentCharset)
        }

        earModels.add(earModel)
      }
    }

    new EarConfigurationImpl(earModels, deployDependencies, earlibDependencies)
  }

  @Override
  void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.messageReporter.createMessage()
      .withGroup(Messages.EAR_CONFIGURATION_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("JEE project import failure")
      .withText("Ear Artifacts may not be configured properly")
      .withException(exception)
      .reportMessage(project)
  }

  @CompileDynamic
  private static Manifest writeToOutputStream(Manifest manifest, OutputStream outputStream) {
    return manifest.writeTo(outputStream)
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
