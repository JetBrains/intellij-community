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

/*
 * User: anna
 * Date: 25-Mar-2010
 */
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.importWizard.EclipseProjectFinder;

import java.io.File;
import java.util.List;
import java.util.Set;

public class EPathUtil {
  static final Logger LOG = Logger.getInstance("#" + EPathUtil.class.getName());

  private EPathUtil() {
  }

  /**
   * @param path path in format /module_root/relative_path
   * @return module_root
   */
  @NotNull
  public static String getRelativeModuleName(String path) {
    int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx > 1 ? path.substring(1, secondSlIdx) : path.substring(1);
  }

  /**
   * @param path path in format /module_root/relative_path
   * @return relative_path or null if /module_root
   */
  @Nullable
  public static String getRelativeToModulePath(String path) {
    final int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx != -1 && secondSlIdx + 1 < path.length() ? path.substring(secondSlIdx + 1) : null;
  }

  public static boolean areUrlsPointTheSame(String ideaUrl, String eclipseUrl) {
    final String path = VfsUtil.urlToPath(eclipseUrl);
    if (ideaUrl.contains(path)) {
      return true;
    }
    else {
      final String relativeToModulePath = getRelativeToModulePath(path);
      final int relativeIdx = ideaUrl.indexOf(relativeToModulePath);
      if (relativeIdx != -1) {
        final String pathToProjectFile = VfsUtil.urlToPath(ideaUrl.substring(0, relativeIdx));
        if (Comparing.strEqual(getRelativeModuleName(path),
                               EclipseProjectFinder.findProjectName(pathToProjectFile))) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  static String expandEclipseRelative2ContentRoots(final @NotNull List<String> currentRoots,
                                              final @NotNull String rootPath,
                                              final @Nullable String relativeToRootPath) {
    for (String currentRoot : currentRoots) {
      if (currentRoot.endsWith(rootPath)
          || Comparing.strEqual(rootPath, EclipseProjectFinder.findProjectName(currentRoot))) { //rootPath = content_root <=> applicable root: abs_path/content_root
        if (relativeToRootPath == null) {
          return VfsUtil.pathToUrl(currentRoot);
        }
        final File relativeToOtherModuleFile = new File(currentRoot, relativeToRootPath);
        if (relativeToOtherModuleFile.exists()) {
          return VfsUtil.pathToUrl(relativeToOtherModuleFile.getPath());
        }
      }
    }
    return null;
  }

  /**
   * @param otherModule check file relative to module content root
   * @param relativeToOtherModule local path (paths inside jars are rejected)
   * @return url
   */
  @Nullable
  static String expandEclipseRelative2OtherModule(final @NotNull Module otherModule, final @Nullable String relativeToOtherModule) {
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(otherModule).getContentRoots();
    for (VirtualFile contentRoot : contentRoots) {
      if (relativeToOtherModule == null) {
        return contentRoot.getUrl();
      }
      final VirtualFile fileUnderModuleContentRoot = contentRoot.findFileByRelativePath(relativeToOtherModule);
      if (fileUnderModuleContentRoot != null) {
        return fileUnderModuleContentRoot.getUrl();
      }
    }
    return null;
  }

  /**
   * @return url
   */
  static String expandEclipsePath2Url(final String path, ModifiableRootModel model, final List<String> currentRoots) {
    final VirtualFile contentRoot = getContentRoot(model);
    LOG.assertTrue(contentRoot != null);
    final String rootPath = contentRoot.getPath();
    String url = null;
    if (new File(path).exists()) {  //absolute path
      url = VfsUtil.pathToUrl(path);
    }
    else {
      final String relativePath = new File(rootPath, path).getPath(); //inside current project
      final File file = new File(relativePath);
      if (file.exists()) {
        url = VfsUtil.pathToUrl(relativePath);
      } else if (path.startsWith("/")) { //relative to other project
        final String moduleName = getRelativeModuleName(path);
        final String relativeToRootPath = getRelativeToModulePath(path);

        final Module otherModule = ModuleManager.getInstance(model.getModule().getProject()).findModuleByName(moduleName);
        if (otherModule != null && otherModule != model.getModule()) {
          url = expandEclipseRelative2OtherModule(otherModule, relativeToRootPath);
        }
        else if (currentRoots != null) {
          url = expandEclipseRelative2ContentRoots(currentRoots, moduleName, relativeToRootPath);
        }
      }
    }
    if (url == null) {
      url = VfsUtil.pathToUrl(path);
    }

    final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(url);
    if (localFile != null) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
      if (jarFile != null) {
        url = jarFile.getUrl();
      }
    }
    return url;
  }

  @Nullable
  public static String collapse2eclipseRelative2OtherModule(final @NotNull Project project, final @NotNull VirtualFile file) {
    final Module module = ModuleUtil.findModuleForFile(file, project);
    if (module != null) {
      return collapse2eclipsePathRelative2Module(file, module);
    } else if (ProjectRootManager.getInstance(project).getFileIndex().isIgnored(file)) { //should check all modules then
      for (Module aModule : ModuleManager.getInstance(project).getModules()) {
        final String path = collapse2eclipsePathRelative2Module(file, aModule);
        if (path != null) {
          return path;
        }
      }
    }
    return null;
  }

  @Nullable
  private static String collapse2eclipsePathRelative2Module(VirtualFile file, Module module) {
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    for (VirtualFile otherRoot : contentRoots) {
      if (VfsUtil.isAncestor(otherRoot, file, false)) {
        return "/" + module.getName() + "/" + VfsUtil.getRelativePath(file, otherRoot, '/');
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile getContentRoot(final ModuleRootModel model) {
   final VirtualFile[] contentRoots = model.getContentRoots();
    for (VirtualFile virtualFile : contentRoots) {
      if (virtualFile.findChild(EclipseXml.PROJECT_FILE) != null) {
        return virtualFile;
      }
    }
    return null;
  }

  static String collapse2EclipsePath(final String url, final ModuleRootModel model) {
    final Project project = model.getModule().getProject();
    final VirtualFile contentRoot = getContentRoot(model);
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file != null) {
      if (file.getFileSystem() instanceof JarFileSystem) {
        file = JarFileSystem.getInstance().getVirtualFileForJar(file);
      }
      LOG.assertTrue(file != null);
      if (contentRoot != null && VfsUtil.isAncestor(contentRoot, file, false)) { //inside current project
        return VfsUtil.getRelativePath(file, contentRoot, '/');
      } else {
        final String path = collapse2eclipseRelative2OtherModule(project, file); //relative to other project
        if (path != null) {
          return path;
        }
      }
      return ProjectRootManagerImpl.extractLocalPath(url);  //absolute path
    }
    else { //try to avoid absolute path for deleted file
      if (contentRoot != null) {
        final String rootUrl = contentRoot.getUrl();
        if (url.startsWith(rootUrl) && url.length() > rootUrl.length()) {
          return url.substring(rootUrl.length() + 1); //without leading /
        }
      }
      final VirtualFile projectBaseDir = contentRoot != null ? contentRoot.getParent() : project.getBaseDir();
      assert projectBaseDir != null;
      final String projectUrl = projectBaseDir.getUrl();
      if (url.startsWith(projectUrl)) {
        return url.substring(projectUrl.length()); //leading /
      }

      return ProjectRootManagerImpl.extractLocalPath(url);
    }
  }

  @Nullable
  static String collapse2EclipseVariabledPath(final LibraryOrderEntry libraryOrderEntry, OrderRootType type) {
    final VirtualFile[] virtualFiles = libraryOrderEntry.getRootFiles(type);
    if (virtualFiles.length > 0) {
      VirtualFile jarFile = virtualFiles[0];
      if (jarFile.getFileSystem() instanceof JarFileSystem) {
        jarFile = JarFileSystem.getInstance().getVirtualFileForJar(jarFile);
      }
      if (jarFile == null) {
        return null;
      }
      final Project project = libraryOrderEntry.getOwnerModule().getProject();
      final VirtualFile baseDir = project.getBaseDir();
      final String filePath = jarFile.getPath();
      if (baseDir != null && !VfsUtil.isAncestor(baseDir, jarFile, false)) {
         final String ideaCollapsed = PathMacroManager.getInstance(project).collapsePath(filePath);
         if (ideaCollapsed.contains("..")) return null;
        final int index = ideaCollapsed.indexOf('$');
        if (index < 0) return null;
        return ideaCollapsed.substring(index).replace("$", "");
      }
    }
    for (String url : libraryOrderEntry.getRootUrls(type)) {
      //check if existing eclipse variable points inside project or doesn't exist
      String filePath = VirtualFileManager.extractPath(url);
      final int jarSeparatorIdx = filePath.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (jarSeparatorIdx > -1) {
        filePath = filePath.substring(0, jarSeparatorIdx);
      }
      final PathMacros pathMacros = PathMacros.getInstance();
      final Set<String> names = pathMacros.getUserMacroNames();
      for (String name : names) {
        final String path = FileUtil.toSystemIndependentName(pathMacros.getValue(name));
        if (filePath.startsWith(path + "/")) {
          final String substr = filePath.substring(path.length());
          return name + (substr.startsWith("/") || substr.length() == 0 ? substr : "/" + substr);
        }
      }
    }
    return null;
  }
}