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
package org.jetbrains.plugins.gradle.tooling.v1_9.builder;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.bundling.War;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.WebConfiguration;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.internal.WebConfigurationImpl;

import java.io.File;
import java.io.StringWriter;
import java.util.*;

/**
 * @author Vladislav.Soroka
 * @since 11/5/13
 */
public class WarModelBuilderImpl implements ModelBuilderService {

  private static final String WEB_APP_DIR_PROPERTY = "webAppDir";
  private static final String WEB_APP_DIR_NAME_PROPERTY = "webAppDirName";

  @Override
  public boolean canBuild(String modelName) {
    return WebConfiguration.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(String modelName, Project project) {
    final WarPlugin warPlugin = project.getPlugins().findPlugin(WarPlugin.class);
    if (warPlugin == null) return null;

    final String webAppDirName = !project.hasProperty(WEB_APP_DIR_NAME_PROPERTY) ?
                                 "src/main/webapp" : String.valueOf(project.property(WEB_APP_DIR_NAME_PROPERTY));

    final File webAppDir = !project.hasProperty(WEB_APP_DIR_PROPERTY)
                           ? new File(project.getProjectDir(), webAppDirName)
                           : (File)project.property(WEB_APP_DIR_PROPERTY);


    List<WebConfiguration.WarModel> warModels = new ArrayList<WebConfiguration.WarModel>();


    for (Task task : project.getTasks()) {
      if (task instanceof War) {
        final WebConfigurationImpl.WarModelImpl warModel = new WebConfigurationImpl.WarModelImpl(((War)task).getArchiveName(), webAppDirName, webAppDir);

        final War warTask = (War)task;
        warModel.setWebXml(warTask.getWebXml());

        final Map<String, Set<String>> webRoots = new HashMap<String, Set<String>>();
        addPath(webRoots, "", webAppDir.getPath());

        warTask.getRootSpec().walk(new Action<CopySpecInternal>() {
          @Override
          public void execute(CopySpecInternal internal) {
            final String relativePath = internal.getDestPath().getPathString();
            internal.getSource().visit(new FileVisitor() {
              @Override
              public void visitDir(FileVisitDetails dirDetails) {
                try {
                  addPath(webRoots, relativePath, dirDetails.getFile().getPath());
                }
                catch (Exception ignore) {
                }
              }

              @Override
              public void visitFile(FileVisitDetails fileDetails) {
                try {
                  if (warTask.getWebXml() == null ||
                      !fileDetails.getFile().getCanonicalPath().equals(warTask.getWebXml().getCanonicalPath())) {
                    addPath(webRoots, relativePath, fileDetails.getFile().getParent());
                  }
                }
                catch (Exception ignore) {
                }
              }
            });
          }
        });

        warModel.setWebRoots(webRoots);
        warModel.setClasspath(warTask.getClasspath().getFiles());

        Manifest manifest = warTask.getManifest();
        if(manifest != null) {
          StringWriter writer = new StringWriter();
          manifest.writeTo(writer);
          warModel.setManifestContent(writer.toString());
        }
        warModels.add(warModel);
      }
    }

    return new WebConfigurationImpl(warModels);
  }

  private static void addPath(Map<String, Set<String>> webRoots, String relativePath, String path) {
    relativePath = relativePath == null ? "" : relativePath;
    Set<String> paths = webRoots.get(relativePath);
    if (paths == null) {
      paths = new LinkedHashSet<String>();
      webRoots.put(relativePath, paths);
    }
    paths.add(path);
  }
}
