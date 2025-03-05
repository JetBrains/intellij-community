// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseModuleManagerImpl;
import org.jetbrains.jps.model.serialization.java.JpsJavaModelSerializerExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.jetbrains.idea.eclipse.conversion.EPathUtil.areUrlsPointTheSame;

/**
 * Read/write .eml
 */
public final class IdeaSpecificSettings {
  public static final @NonNls String RELATIVE_MODULE_SRC = "relative-module-src";
  public static final @NonNls String RELATIVE_MODULE_CLS = "relative-module-cls";
  public static final @NonNls String RELATIVE_MODULE_JAVADOC = "relative-module-javadoc";
  public static final @NonNls String PROJECT_RELATED = "project-related";

  public static final @NonNls String SRCROOT_ATTR = "srcroot";
  public static final @NonNls String SRCROOT_BIND_ATTR = "bind";
  private static final Logger LOG = Logger.getInstance(IdeaSpecificSettings.class);
  public static final @NonNls String JAVADOCROOT_ATTR = "javadocroot_attr";
  public static final String INHERIT_JDK = "inheritJdk";


  public static boolean writeIdeaSpecificClasspath(@NotNull Element root, @NotNull ModuleRootModel model) {
    boolean isModified = false;

    CompilerModuleExtension compilerModuleExtension = model.getModuleExtension(CompilerModuleExtension.class);
    if (compilerModuleExtension.getCompilerOutputUrlForTests() != null) {
      final Element pathElement = new Element(IdeaXml.OUTPUT_TEST_TAG);
      pathElement.setAttribute(IdeaXml.URL_ATTR, compilerModuleExtension.getCompilerOutputUrlForTests());
      root.addContent(pathElement);
      isModified = true;
    }
    if (compilerModuleExtension.isCompilerOutputPathInherited()) {
      root.setAttribute(JpsJavaModelSerializerExtension.INHERIT_COMPILER_OUTPUT_ATTRIBUTE, String.valueOf(true));
      isModified = true;
    }
    if (compilerModuleExtension.isExcludeOutput()) {
      root.addContent(new Element(IdeaXml.EXCLUDE_OUTPUT_TAG));
      isModified = true;
    }

    LanguageLevelModuleExtension languageLevelModuleExtension = model.getModuleExtension(LanguageLevelModuleExtension.class);
    LanguageLevel languageLevel = languageLevelModuleExtension.getLanguageLevel();
    if (languageLevel != null) {
      root.setAttribute(JpsJavaModelSerializerExtension.MODULE_LANGUAGE_LEVEL_ATTRIBUTE, languageLevel.name());
      isModified = true;
    }

    for (ContentEntry entry : model.getContentEntries()) {
      Element contentEntryElement = new Element(IdeaXml.CONTENT_ENTRY_TAG);
      contentEntryElement.setAttribute(IdeaXml.URL_ATTR, entry.getUrl());
      root.addContent(contentEntryElement);
      for (SourceFolder sourceFolder : entry.getSourceFolders()) {
        if (sourceFolder.isTestSource()) {
          Element element = new Element(IdeaXml.TEST_FOLDER_TAG);
          contentEntryElement.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, sourceFolder.getUrl());
          isModified = true;
        }
        String packagePrefix = sourceFolder.getPackagePrefix();
        if (!StringUtil.isEmptyOrSpaces(packagePrefix)) {
          Element element = new Element(IdeaXml.PACKAGE_PREFIX_TAG);
          contentEntryElement.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, sourceFolder.getUrl());
          element.setAttribute(IdeaXml.PACKAGE_PREFIX_VALUE_ATTR, packagePrefix);
          isModified = true;
        }
      }

      VirtualFile entryFile = entry.getFile();
      for (ExcludeFolder excludeFolder : entry.getExcludeFolders()) {
        VirtualFile excludeFile = excludeFolder.getFile();
        if (entryFile == null || excludeFile == null || VfsUtilCore.isAncestor(entryFile, excludeFile, false)) {
          Element element = new Element(IdeaXml.EXCLUDE_FOLDER_TAG);
          contentEntryElement.addContent(element);
          element.setAttribute(IdeaXml.URL_ATTR, excludeFolder.getUrl());
          isModified = true;
        }
      }
    }

    Map<String, String> libLevels = new LinkedHashMap<>();
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        final DependencyScope scope = ((ModuleOrderEntry)entry).getScope();
        if (!scope.equals(DependencyScope.COMPILE)) {
          Element element = new Element("module");
          element.setAttribute("name", ((ModuleOrderEntry)entry).getModuleName());
          element.setAttribute("scope", scope.name());
          root.addContent(element);
          isModified = true;
        }
      }
      if (entry instanceof JdkOrderEntry) {
        final Sdk jdk = ((JdkOrderEntry)entry).getJdk();
        if (EclipseModuleManagerImpl.getInstance(entry.getOwnerModule()).getInvalidJdk() != null || jdk != null) {
          if (entry instanceof InheritedJdkOrderEntry) {
            root.setAttribute(INHERIT_JDK, "true");
          }
          else {
            String jdkName = ((JdkOrderEntry)entry).getJdkName();
            if (jdkName != null) {
              root.setAttribute("jdk", jdkName);
            }
            if (jdk != null) {
              root.setAttribute("jdk_type", jdk.getSdkType().getName());
            }
          }
          isModified = true;
        }
      }
      if (!(entry instanceof LibraryOrderEntry libraryEntry)) continue;

      Element element = new Element("lib");

      String libraryName = libraryEntry.getLibraryName();
      if (libraryName == null) {
        final String[] urls = libraryEntry.getRootUrls(OrderRootType.CLASSES);
        if (urls.length > 0) {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(urls[0]);
          final VirtualFile fileForJar = JarFileSystem.getInstance().getVirtualFileForJar(file);
          if (fileForJar != null) {
            file = fileForJar;
          }
          libraryName = file != null ? file.getName() : null;
        }
        if (libraryName == null) {
          libraryName = libraryEntry.getPresentableName();
        }
      }

      element.setAttribute("name", libraryName);
      DependencyScope scope = libraryEntry.getScope();
      element.setAttribute("scope", scope.name());
      if (libraryEntry.isModuleLevel()) {
        final String[] urls = libraryEntry.getRootUrls(OrderRootType.SOURCES);
        String eclipseUrl = null;
        if (urls.length > 0) {
          eclipseUrl = urls[0];
          final int jarSeparatorIdx = urls[0].indexOf(JarFileSystem.JAR_SEPARATOR);
          if (jarSeparatorIdx > -1) {
            eclipseUrl = eclipseUrl.substring(0, jarSeparatorIdx);
          }
        }
        for (String url : urls) {
          Element srcElement = new Element(SRCROOT_ATTR);
          srcElement.setAttribute("url", url);
          if (!areUrlsPointTheSame(url, eclipseUrl)) {
            srcElement.setAttribute(SRCROOT_BIND_ATTR, String.valueOf(false));
          }
          element.addContent(srcElement);
        }

        final String[] javadocUrls = libraryEntry.getRootUrls(JavadocOrderRootType.getInstance());
        for (int i = 1; i < javadocUrls.length; i++) {
          Element javadocElement = new Element(JAVADOCROOT_ATTR);
          javadocElement.setAttribute("url", javadocUrls[i]);
          element.addContent(javadocElement);
        }

        for (String srcUrl : libraryEntry.getRootUrls(OrderRootType.SOURCES)) {
          appendModuleRelatedRoot(element, srcUrl, RELATIVE_MODULE_SRC, model);
        }

        for (String classesUrl : libraryEntry.getRootUrls(OrderRootType.CLASSES)) {
          appendModuleRelatedRoot(element, classesUrl, RELATIVE_MODULE_CLS, model);
        }

        for (String javadocUrl : libraryEntry.getRootUrls(JavadocOrderRootType.getInstance())) {
          appendModuleRelatedRoot(element, javadocUrl, RELATIVE_MODULE_JAVADOC, model);
        }

        if (!element.getChildren().isEmpty()) {
          root.addContent(element);
          isModified = true;
          continue;
        }
      }
      else {
        String libraryLevel = libraryEntry.getLibraryLevel();
        if (!LibraryTablesRegistrar.APPLICATION_LEVEL.equals(libraryLevel)) {
          libLevels.put(libraryEntry.getLibraryName(), libraryLevel);
        }
      }
      if (!scope.equals(DependencyScope.COMPILE)) {
        root.addContent(element);
        isModified = true;
      }
    }

    if (!libLevels.isEmpty()) {
      Element libLevelsElement = new Element("levels");
      for (String libName : libLevels.keySet()) {
        Element libElement = new Element("level");
        libElement.setAttribute("name", libName);
        libElement.setAttribute("value", libLevels.get(libName));
        libLevelsElement.addContent(libElement);
      }
      root.addContent(libLevelsElement);
    }

    PathMacroManager.getInstance(model.getModule()).collapsePaths(root);

    return isModified;
  }

  public static void appendModuleRelatedRoot(Element element, String classesUrl, final String rootName, ModuleRootModel model) {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(classesUrl);
    if (file == null) {
      return;
    }

    final Project project = model.getModule().getProject();
    if (file.getFileSystem() instanceof JarFileSystem) {
      file = JarFileSystem.getInstance().getVirtualFileForJar(file);
      assert file != null;
    }
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null) {
      appendRelatedToModule(element, classesUrl, rootName, file, module);
    }
    else if (ProjectRootManager.getInstance(project).getFileIndex().isExcluded(file)) {
      for (Module aModule : ModuleManager.getInstance(project).getModules()) {
        if (appendRelatedToModule(element, classesUrl, rootName, file, aModule)) {
          return;
        }
      }
    }
  }

  private static boolean appendRelatedToModule(Element element, String classesUrl, String rootName, VirtualFile file, Module module) {
    for (VirtualFile contentRoot : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (VfsUtilCore.isAncestor(contentRoot, file, false)) {
        final Element clsElement = new Element(rootName);
        clsElement.setAttribute(PROJECT_RELATED, PathMacroManager.getInstance(module.getProject()).collapsePath(classesUrl));
        element.addContent(clsElement);
        return true;
      }
    }
    return false;
  }
}
