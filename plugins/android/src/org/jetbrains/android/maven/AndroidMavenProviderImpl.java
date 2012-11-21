/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.maven;

import com.android.SdkConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.compiler.AndroidCompileUtil;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.compiler.MavenResourceCompiler;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenResource;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidMavenProviderImpl implements AndroidMavenProvider {

  public static void setPathsToDefault(MavenProject mavenProject, Module module, AndroidFacetConfiguration configuration) {
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(module);
    String genSources = FileUtil.toSystemIndependentName(mavenProject.getGeneratedSourcesDirectory(false));

    if (VfsUtil.isAncestor(new File(moduleDirPath), new File(genSources), true)) {
      String genRelativePath = FileUtil.getRelativePath(moduleDirPath, genSources, '/');
      if (genRelativePath != null) {
        configuration.GEN_FOLDER_RELATIVE_PATH_APT = '/' + genRelativePath + "/r";
        configuration.GEN_FOLDER_RELATIVE_PATH_AIDL = '/' + genRelativePath + "/aidl";
      }
    }

    String buildDirectory = FileUtil.toSystemIndependentName(mavenProject.getBuildDirectory());

    if (VfsUtil.isAncestor(new File(moduleDirPath), new File(buildDirectory), true)) {
      String buildDirRelPath = FileUtil.getRelativePath(moduleDirPath, buildDirectory, '/');
      configuration.APK_PATH = '/' + buildDirRelPath + '/' + AndroidCompileUtil.getApkName(module);
    }
  }

  public static void configureAaptCompilation(MavenProject mavenProject,
                                              Module module,
                                              AndroidFacetConfiguration configuration,
                                              boolean hasApkSources) {
    String moduleDirPath = AndroidRootUtil.getModuleDirPath(module);
    String genSources = FileUtil.toSystemIndependentName(mavenProject.getGeneratedSourcesDirectory(false));

    if (VfsUtil.isAncestor(new File(moduleDirPath), new File(genSources), true)) {
      String genRelativePath = FileUtil.getRelativePath(moduleDirPath, genSources, '/');
      if (genRelativePath != null) {
        configuration.USE_CUSTOM_APK_RESOURCE_FOLDER = hasApkSources;
        configuration.CUSTOM_APK_RESOURCE_FOLDER = '/' + genRelativePath + "/combined-resources/" + SdkConstants.FD_RES;
      }
    }

    configuration.RUN_PROCESS_RESOURCES_MAVEN_TASK = hasApkSources;
  }

  static boolean processResources(@NotNull Module module,
                                  @NotNull MavenProject mavenProject,
                                  ResourceProcessor processor) {
    for (MavenResource resource : mavenProject.getResources()) {
      if (resource.isFiltered()) {
        VirtualFile resDir = LocalFileSystem.getInstance().findFileByPath(resource.getDirectory());
        if (resDir == null) continue;

        List<Pattern> includes = MavenResourceCompiler.collectPatterns(resource.getIncludes(), "**/*");
        List<Pattern> excludes = MavenResourceCompiler.collectPatterns(resource.getExcludes(), null);
        final String resourceTargetPath = resource.getTargetPath();
        if (resourceTargetPath != null) {
          String targetPath = FileUtil.toSystemIndependentName(resourceTargetPath);

          if (processResources(module.getProject(), resDir, resDir, includes, excludes, targetPath, processor)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  static boolean processResources(Project project,
                                  VirtualFile sourceRoot,
                                  VirtualFile file,
                                  List<Pattern> includes,
                                  List<Pattern> excludes,
                                  String resOutputDir,
                                  ResourceProcessor processor) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (!fileIndex.isIgnored(file)) {
      String relPath = VfsUtilCore.getRelativePath(file, sourceRoot, '/');
      if (relPath != null && MavenUtil.isIncluded(relPath, includes, excludes)) {
        if (processor.process(file, resOutputDir + "/" + relPath)) {
          return true;
        }
      }
    }
    if (file.isDirectory()) {
      for (VirtualFile child : file.getChildren()) {
        if (processResources(project, sourceRoot, child, includes, excludes, resOutputDir, processor)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean isMavenizedModule(@NotNull Module module) {
    MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(module.getProject());
    return mavenProjectsManager != null ? mavenProjectsManager.isMavenizedModule(module) : null;
  }

  @NotNull
  public List<File> getMavenDependencyArtifactFiles(@NotNull Module module) {
    MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);
    List<File> result = new ArrayList<File>();
    if (mavenProject != null) {
      for (MavenArtifact depArtifact : mavenProject.getDependencies()) {
        if (AndroidMavenUtil.APKSOURCES_DEPENDENCY_TYPE.equals(depArtifact.getType())) {
          result.add(MavenArtifactUtil.getArtifactFile(mavenProject.getLocalRepository(), depArtifact.getMavenId()));
        }
      }
    }
    return result;
  }

  @Nullable
  @Override
  public String getBuildDirectory(@NotNull Module module) {
    MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);
    if (mavenProject != null) {
      return mavenProject.getBuildDirectory();
    }
    return null;
  }

  @Override
  public void setPathsToDefault(@NotNull Module module, AndroidFacetConfiguration facetConfiguration) {
    MavenProject mavenProject = MavenProjectsManager.getInstance(module.getProject()).findProject(module);
    if (mavenProject != null) {
      setPathsToDefault(mavenProject, module, facetConfiguration);
      if (hasApkSourcesDependency(mavenProject)) {
        configureAaptCompilation(mavenProject, module, facetConfiguration, true);
      }
    }
  }

  public static boolean hasApkSourcesDependency(MavenProject mavenProject) {
    for (MavenArtifact artifact : mavenProject.getDependencies()) {
      if (AndroidMavenUtil.APKSOURCES_DEPENDENCY_TYPE.equals(artifact.getType())) {
        return true;
      }
    }
    return false;
  }

  interface ResourceProcessor {
    boolean process(@NotNull VirtualFile resource, @NotNull String outputPath);
  }
}
