/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package org.jetbrains.plugins.gradle.tooling.builder

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.java.archives.Manifest
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.tasks.bundling.War
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.gradle.model.web.WebConfiguration
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService
import org.jetbrains.plugins.gradle.tooling.internal.web.WarModelImpl
import org.jetbrains.plugins.gradle.tooling.internal.web.WebConfigurationImpl
import org.jetbrains.plugins.gradle.tooling.internal.web.WebResourceImpl

/**
 * @author Vladislav.Soroka
 * @since 6/25/2014
 */
class WarModelBuilderImpl implements ModelBuilderService {

  private static final String WEB_APP_DIR_PROPERTY = "webAppDir"
  private static final String WEB_APP_DIR_NAME_PROPERTY = "webAppDirName"

  @Override
  public boolean canBuild(String modelName) {
    return WebConfiguration.name.equals(modelName)
  }

  @Nullable
  @Override
  public Object buildAll(String modelName, Project project) {
    final WarPlugin warPlugin = project.plugins.findPlugin(WarPlugin)
    if (warPlugin == null) return null

    final String webAppDirName = !project.hasProperty(WEB_APP_DIR_NAME_PROPERTY) ?
                                 "src/main/webapp" : String.valueOf(project.property(WEB_APP_DIR_NAME_PROPERTY))

    final File webAppDir = !project.hasProperty(WEB_APP_DIR_PROPERTY) ? new File(project.projectDir, webAppDirName) :
                           (File)project.property(WEB_APP_DIR_PROPERTY)

    def warModels = []

    project.tasks.each { Task task ->
      if (task instanceof War) {
        final WarModelImpl warModel =
          new WarModelImpl((task as War).archiveName, webAppDirName, webAppDir)

        final List<WebConfiguration.WebResource> webResources = []
        final War warTask = task as War
        warModel.webXml = warTask.webXml
        warTask.rootSpec.setIncludeEmptyDirs(true)

        warTask.rootSpec.walk({ def resolver ->
          // def resolver ->
          //      in Gradle v1.x - org.gradle.api.internal.file.copy.CopySpecInternal
          //      in Gradle v2.x - org.gradle.api.internal.file.copy.CopySpecResolver

          if (resolver.metaClass.respondsTo(resolver, 'setIncludeEmptyDirs', boolean)) {
            resolver.setIncludeEmptyDirs(true)
          }
          if (!resolver.metaClass.respondsTo(resolver, 'getDestPath') ||
              !resolver.metaClass.respondsTo(resolver, 'getSource')) {
            throw new RuntimeException("${GradleVersion.current()} is not supported by web artifact importer")
          }

          final String relativePath = resolver.destPath.pathString
          final def sourcePaths

          if (resolver.metaClass.respondsTo(resolver, 'getSourcePaths')) {
            sourcePaths = resolver.getSourcePaths()
          } else if (resolver.hasProperty('sourcePaths')) {
            sourcePaths = resolver.sourcePaths
          } else if (resolver.hasProperty('this$0') && resolver.this$0.metaClass.respondsTo(resolver, 'getSourcePaths')) {
            sourcePaths = resolver.this$0.getSourcePaths()
          } else if (resolver.hasProperty('this$0') && resolver.this$0.hasProperty('sourcePaths')) {
            sourcePaths = resolver.this$0.sourcePaths
          } /*else {
            throw new RuntimeException("${GradleVersion.current()} is not supported by web artifact importer")
          }*/

          if(sourcePaths) {
            (sourcePaths.flatten() as List).each { def path ->
              if (path instanceof String) {
                def file = new File(warTask.project.projectDir, path)
                addPath(webResources, relativePath, "", file)
              }
            }
          }

          resolver.source.visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
              try {
                addPath(webResources, relativePath, dirDetails.path, dirDetails.file)
              }
              catch (Exception ignore) {
              }
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
              try {
                if (warTask.webXml == null ||
                    !fileDetails.file.canonicalPath.equals(warTask.webXml.canonicalPath)) {
                  addPath(webResources, relativePath, fileDetails.path, fileDetails.file)
                }
              }
              catch (Exception ignore) {
              }
            }
          })
        })

        warModel.webResources = webResources
        warModel.classpath = warTask.classpath.files

        Manifest manifest = warTask.manifest
        if (manifest != null) {
          def writer = new StringWriter()
          manifest.writeTo(writer)
          warModel.manifestContent = writer.toString()
        }
        warModels.add(warModel)
      }
    }

    new WebConfigurationImpl(warModels)
  }

  @NotNull
  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    ErrorMessageBuilder.create(
      project, e, "Web project import errors"
    ).withDescription("Web Facets/Artifacts will not be configured")
  }

  private static addPath(List<WebConfiguration.WebResource> webResources, String warRelativePath, String fileRelativePath, File file) {
    warRelativePath = warRelativePath == null ? "" : warRelativePath

    WebConfiguration.WebResource webResource = new WebResourceImpl(warRelativePath, fileRelativePath, file)
    webResources.add(webResource)
  }
}
