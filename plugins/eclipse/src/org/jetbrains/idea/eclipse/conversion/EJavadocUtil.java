// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.util.Function;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EPathCommonUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.jetbrains.idea.eclipse.EclipseXml.*;
import static org.jetbrains.idea.eclipse.conversion.EPathUtil.*;

/**
 * Eclipse javadoc format:
 * <ul>
 * <li>jar:platform:/resource/project_name/relative_path_to_zip|jar!/path_inside - current or another
 * <li>jar:file:/absolute_path_to_zip|jar!/path_inside
 * <li>file:/absolute_path
 * <li>http://www.javadoc.url
 * </ul>
 */
public final class EJavadocUtil {
  private EJavadocUtil() {
  }

  static void appendJavadocRoots(Element element,
                                 ModuleRootModel model,
                                 final List<String> currentRoots,
                                 Library.ModifiableModel modifiableModel) {
    final Element attributes = element.getChild("attributes");
    if (attributes == null) {
      return;
    }
    for (Element attribute : attributes.getChildren("attribute")) {
      if (Comparing.strEqual(attribute.getAttributeValue("name"), JAVADOC_LOCATION)) {
        String javadocPath = attribute.getAttributeValue("value");
        if (!SystemInfo.isWindows) {
          javadocPath = javadocPath.replaceFirst(FILE_PROTOCOL, FILE_PROTOCOL + "/");
        }
        modifiableModel.addRoot(toIdeaJavadocUrl(model, javadocPath, currentRoots), JavadocOrderRootType.getInstance());
      }
    }
  }

  private static String toIdeaJavadocUrl(ModuleRootModel model, String javadocPath, List<String> currentRoots) {
    if (javadocPath.startsWith(FILE_PROTOCOL)) {
      if (new File(javadocPath.substring(FILE_PROTOCOL.length())).exists()) {
        return VfsUtilCore.pathToUrl(javadocPath.substring(FILE_PROTOCOL.length()));
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
          final String relativeToPlatform = jarJavadocPath.substring(PLATFORM_PROTOCOL.length() + "resource".length()); // starts with leading /
          final VirtualFile currentRoot = getContentRoot(model);
          final String currentModulePath = (currentRoot != null ? currentRoot.getParent().getPath() : model.getModule().getProject().getBaseDir().getPath()) + relativeToPlatform;
          if (isJarFileExist(currentModulePath)) {
            return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, currentModulePath);
          }
          else {
            final String moduleName = EPathCommonUtil.getRelativeModuleName(relativeToPlatform);
            final String relativeToModulePathWithJarSuffix = EPathCommonUtil.getRelativeToModulePath(relativeToPlatform);
            final String relativeToModulePath = stripPathInsideJar(relativeToModulePathWithJarSuffix);
            final Module otherModule = ModuleManager.getInstance(model.getModule().getProject()).findModuleByName(moduleName);
            String url = null;
            if (otherModule != null && otherModule != model.getModule()) {
              url = expandEclipseRelative2OtherModule(otherModule, relativeToModulePath);
            }
            else if (currentRoots != null) {
              url = EPathCommonUtil.expandEclipseRelative2ContentRoots(currentRoots, moduleName, relativeToModulePath);
            }

            if (url != null) {
              assert relativeToModulePathWithJarSuffix != null;
              assert relativeToModulePath != null;
              if (relativeToModulePath.length() < relativeToModulePathWithJarSuffix.length()) {
                url += relativeToModulePathWithJarSuffix.substring(relativeToModulePath.length());
              }
              return VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, VfsUtilCore.urlToPath(url));
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
  public static String stripPathInsideJar(@Nullable String relativeToModulePathWithJarSuffix) {
    String relativeToModulePath = relativeToModulePathWithJarSuffix;
    if (relativeToModulePath != null) {
      int jarSufIdx = relativeToModulePathWithJarSuffix.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (jarSufIdx != -1) {
        relativeToModulePath = relativeToModulePath.substring(0, jarSufIdx);
      }
    }
    return relativeToModulePath;
  }

  public static boolean isJarFileExist(String path) {
    final int jarSufIdx = path.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (jarSufIdx != -1) {
      path = path.substring(0, jarSufIdx);
    }
    return new File(path).exists();
  }

  private static String toEclipseJavadocPath(ModuleRootModel model, String javadocPath) {
    final String protocol = VirtualFileManager.extractProtocol(javadocPath);
    if (!Comparing.strEqual(protocol, HttpFileSystem.getInstance().getProtocol())) {
      final String path = VfsUtilCore.urlToPath(javadocPath);
      final VirtualFile contentRoot = getContentRoot(model);
      final Project project = model.getModule().getProject();
      final VirtualFile baseDir = contentRoot != null ? contentRoot.getParent() : project.getBaseDir();
      if (Comparing.strEqual(protocol, JarFileSystem.getInstance().getProtocol())) {
        final VirtualFile javadocFile =
          JarFileSystem.getInstance().getVirtualFileForJar(VirtualFileManager.getInstance().findFileByUrl(javadocPath));
        if (javadocFile != null) {
          final String relativeUrl;
          if (contentRoot != null && VfsUtilCore.isAncestor(contentRoot, javadocFile, false)) {
            relativeUrl = "/" + VfsUtilCore.getRelativePath(javadocFile, baseDir, '/');
          } else {
            relativeUrl = collapse2eclipseRelative2OtherModule(project, javadocFile);
          }
          if (relativeUrl != null) {
            if (!javadocPath.contains(JarFileSystem.JAR_SEPARATOR)) {
              javadocPath = StringUtil.trimEnd(javadocPath, "/") + JarFileSystem.JAR_SEPARATOR;
            }
            javadocPath = JAR_PREFIX +
                          PLATFORM_PROTOCOL +
                          "resource" +
                          relativeUrl +
                          javadocPath.substring(javadocFile.getUrl().length() - 1);
          }
          else {
            LOG.info("Javadoc path: " + javadocPath);
            final Module module = ModuleUtilCore.findModuleForFile(javadocFile, project);
            LOG.info("Module: " + (module != null ? module.getName() : "not found"));
            if (module != null) {
              LOG.info("Content roots: " + Arrays.toString(ModuleRootManager.getInstance(module).getContentRoots()));
            }
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

  static void setupJavadocAttributes(Element orderEntry, LibraryOrderEntry libraryOrderEntry, final ModuleRootModel model) {
    setupAttributes(orderEntry, s -> toEclipseJavadocPath(model, s), JAVADOC_LOCATION, libraryOrderEntry.getRootUrls(JavadocOrderRootType.getInstance()));
  }

  public static <T> void setupAttributes(Element orderEntry,
                                         Function<? super T, String> fun,
                                         String attributeName,
                                         T[] roots) {
    final List<String> eclipseUrls = new ArrayList<>();
    if (roots.length > 0) {
      eclipseUrls.add(fun.fun(roots[0]));
    }

    final List<Element> children = new ArrayList<>(orderEntry.getChildren(ATTRIBUTES_TAG));
    for (Element attsElement : children) {
      final ArrayList<Element> attTags = new ArrayList<>(attsElement.getChildren(ATTRIBUTE_TAG));
      for (Element attElement : attTags) {
        if (Comparing.strEqual(attElement.getAttributeValue("name"), attributeName)) {
          final String javadocPath = attElement.getAttributeValue("value");
          if (!eclipseUrls.remove(javadocPath)) {
            attElement.detach();
          }
        }
      }
    }

    for (final String docUrl : eclipseUrls) {
      Element child = orderEntry.getChild(ATTRIBUTES_TAG);
      if (child == null) {
        child = new Element(ATTRIBUTES_TAG);
        orderEntry.addContent(child);
      }

      final Element attrElement = new Element(ATTRIBUTE_TAG);
      child.addContent(attrElement);
      attrElement.setAttribute("name", attributeName);
      attrElement.setAttribute("value", docUrl);
    }
  }
}