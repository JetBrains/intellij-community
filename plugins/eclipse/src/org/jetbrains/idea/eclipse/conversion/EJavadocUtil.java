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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.idea.eclipse.EclipseXml.*;
import static org.jetbrains.idea.eclipse.conversion.ERelativePathUtil.*;

public class EJavadocUtil {
  private EJavadocUtil() {
  }

  @Nullable
  static List<String> getJavadocAttribute(Element element, ModuleRootModel model, final List<String> currentRoots) {
    Element attributes = element.getChild("attributes");
    if (attributes == null) {
      return null;
    }
    List<String> result = new ArrayList<String>();
    for (Object o : attributes.getChildren("attribute")) {
      if (Comparing.strEqual(((Element)o).getAttributeValue("name"), "javadoc_location")) {
        Element attribute = (Element)o;
        String javadocPath = attribute.getAttributeValue("value");
        if (!SystemInfo.isWindows) {
          javadocPath = javadocPath.replaceFirst(FILE_PROTOCOL, FILE_PROTOCOL + "/");
        }
        result.add(toIdeaJavadocUrl(model, javadocPath, currentRoots));
      }
    }
    return result;
  }

  static String toIdeaJavadocUrl(ModuleRootModel model, String javadocPath, List<String> currentRoots) {
    if (javadocPath.startsWith(FILE_PROTOCOL)) {
      if (new File(javadocPath.substring(FILE_PROTOCOL.length())).exists()) {
        return VfsUtil.pathToUrl(javadocPath.substring(FILE_PROTOCOL.length()));
      }
    }
    else {
      final String protocol = VirtualFileManager.extractProtocol(javadocPath);
      if (Comparing.strEqual(protocol, HttpFileSystem.getInstance().getProtocol())) {
        return javadocPath;
      }
      else if (javadocPath.startsWith(JAR_PREFIX)) {
        final String jarJavadocPath = javadocPath.substring(JAR_PREFIX.length());
        if (jarJavadocPath.startsWith(PLATFORM_PROTOCOL)) {
          String relativeToPlatform = jarJavadocPath.substring(PLATFORM_PROTOCOL.length() + "resource".length());
          final String currentModulePath = getCurrentRoot(model).getParent().getPath() + relativeToPlatform;
          if (isJarFileExist(currentModulePath)) {
            return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, currentModulePath);
          }
          else {
            final String moduleName = getRootPath(relativeToPlatform);
            final String relativeToModulePathWithJarSuffix = getRelativeToRootPath(relativeToPlatform);
            final String relativeToModulePath = stripPathInsideJar(relativeToModulePathWithJarSuffix);
            final Module otherModule = ModuleManager.getInstance(model.getModule().getProject()).findModuleByName(moduleName);
            String url = null;
            if (otherModule != null && otherModule != model.getModule()) {
              url = relativeToOtherModule(otherModule, relativeToModulePath);
            }
            else if (currentRoots != null) {
              url = relativeToContentRoots(currentRoots, getCurrentRoot(model).getPath(), relativeToModulePath);
            }

            if (url != null) {
              assert relativeToModulePathWithJarSuffix != null;
              assert relativeToModulePath != null;
              if (relativeToModulePath.length() < relativeToModulePathWithJarSuffix.length()) {
                url += relativeToModulePathWithJarSuffix.substring(relativeToModulePath.length());
              }
              return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, VfsUtil.urlToPath(url));
            }
          }
        }
        else if (jarJavadocPath.startsWith(FILE_PROTOCOL)) {
          final String localFile = jarJavadocPath.substring(FILE_PROTOCOL.length());
          if (isJarFileExist(localFile)) {
            return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, localFile);
          }
        }
      }
    }
    return javadocPath;
  }

  @Nullable
  private static String stripPathInsideJar(@Nullable String relativeToModulePathWithJarSuffix) {
    String relativeToModulePath = relativeToModulePathWithJarSuffix;
    if (relativeToModulePath != null) {
      int jarSufIdx = relativeToModulePathWithJarSuffix.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (jarSufIdx != -1) {
        relativeToModulePath = relativeToModulePath.substring(0, jarSufIdx);
      }
    }
    return relativeToModulePath;
  }

  static boolean isJarFileExist(String path) {
    final int jarSufIdx = path.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (jarSufIdx != -1) {
      path = path.substring(0, jarSufIdx);
    }
    return new File(path).exists();
  }

  static String toEclipseJavadocPath(ModuleRootModel model, String javadocPath) {
    final String protocol = VirtualFileManager.extractProtocol(javadocPath);
    if (!Comparing.strEqual(protocol, HttpFileSystem.getInstance().getProtocol())) {
      final String path = VfsUtil.urlToPath(javadocPath);
      final VirtualFile contentRoot = getCurrentRoot(model);
      final Project project = model.getModule().getProject();
      final VirtualFile baseDir = contentRoot != null ? contentRoot.getParent() : project.getBaseDir();
      if (Comparing.strEqual(protocol, JarFileSystem.getInstance().getProtocol())) {
        final VirtualFile javadocFile =
          JarFileSystem.getInstance().getVirtualFileForJar(VirtualFileManager.getInstance().findFileByUrl(javadocPath));
        if (javadocFile != null) {
          String relativeUrl = relativeToOtherModule(project, javadocFile);
          if (relativeUrl == null && VfsUtil.isAncestor(baseDir, javadocFile, false)) {
            relativeUrl = "/" + VfsUtil.getRelativePath(javadocFile, baseDir, '/');
          }
          if (relativeUrl != null) {
            if (javadocPath.indexOf(JarFileSystem.JAR_SEPARATOR) == -1) {
              javadocPath = StringUtil.trimEnd(javadocPath, "/") + JarFileSystem.JAR_SEPARATOR;
            }
            javadocPath = JAR_PREFIX +
                          PLATFORM_PROTOCOL +
                          "resource" +
                          relativeUrl +
                          javadocPath.substring(javadocFile.getUrl().length() - 1);
          }
          else {
            javadocPath = JAR_PREFIX + FILE_PROTOCOL + StringUtil.trimStart(path, "/");
          }
        }
        else {
          javadocPath = JAR_PREFIX + FILE_PROTOCOL + StringUtil.trimStart(path, "/");
        }
      }
      else if (new File(path).exists()) {
        javadocPath = FILE_PROTOCOL + StringUtil.trimStart(path, "/");
      }
    }
    return javadocPath;
  }

  private static VirtualFile getCurrentRoot(ModuleRootModel model) {
    final VirtualFile contentRoot = getContentRoot(model);
    assert contentRoot != null;
    return contentRoot;
  }
}