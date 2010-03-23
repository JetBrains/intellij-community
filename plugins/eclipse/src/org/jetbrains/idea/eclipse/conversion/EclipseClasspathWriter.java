/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Date: 11-Nov-2008
 */
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseModuleManager;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EclipseClasspathWriter {
  private ModuleRootModel myModel;
  private Map<String, Element> myOldEntries = new HashMap<String, Element>();

  public EclipseClasspathWriter(final ModuleRootModel model) {
    myModel = model;
  }

  public void writeClasspath(Element classpathElement, @Nullable Element oldRoot) throws ConversionException {
    if (oldRoot != null) {
      for (Object o : oldRoot.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
        final Element oldChild = (Element)o;
        final String oldKind = oldChild.getAttributeValue(EclipseXml.KIND_ATTR);
        final String oldPath = oldChild.getAttributeValue(EclipseXml.PATH_ATTR);
        myOldEntries.put(oldKind + getJREKey(oldPath), oldChild);
      }
    }

    for (OrderEntry orderEntry : myModel.getOrderEntries()) {
      createClasspathEntry(orderEntry, classpathElement);
    }

    @NonNls String outputPath = "bin";
    if (myModel.getContentEntries().length == 1) {
      final VirtualFile contentRoot = myModel.getContentEntries()[0].getFile();
      final VirtualFile output = myModel.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputPath();
      if (contentRoot != null && output != null && VfsUtil.isAncestor(contentRoot, output, false)) {
        outputPath = getRelativePath(output.getUrl());
      }
      else if (output == null) {
        final String url = myModel.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputUrl();
        if (url != null) {
          outputPath = getRelativePath(url);
        }
      }
    }
    final Element orderEntry = addOrderEntry(EclipseXml.OUTPUT_KIND, outputPath, classpathElement);
    setAttributeIfAbsent(orderEntry, EclipseXml.PATH_ATTR, EclipseXml.BIN_DIR);
  }

  private void createClasspathEntry(OrderEntry entry, Element classpathRoot) throws ConversionException {
    if (entry instanceof ModuleSourceOrderEntry) {
      final ContentEntry[] entries = ((ModuleSourceOrderEntry)entry).getRootModel().getContentEntries();
      if (entries.length > 0) {
        final ContentEntry contentEntry = entries[0];
        for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          addOrderEntry(EclipseXml.SRC_KIND, getRelativePath(sourceFolder.getUrl()), classpathRoot);
        }
      }
    }
    else if (entry instanceof ModuleOrderEntry) {
      Element orderEntry = addOrderEntry(EclipseXml.SRC_KIND, "/" + ((ModuleOrderEntry)entry).getModuleName(), classpathRoot);
      setAttributeIfAbsent(orderEntry, EclipseXml.COMBINEACCESSRULES_ATTR, EclipseXml.FALSE_VALUE);
      setExported(orderEntry, ((ExportableOrderEntry)entry));
    }
    else if (entry instanceof LibraryOrderEntry) {
      final LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)entry;
      final String libraryName = libraryOrderEntry.getLibraryName();
      if (libraryOrderEntry.isModuleLevel()) {
        final String[] files = libraryOrderEntry.getUrls(OrderRootType.CLASSES);
        if (files.length > 0) {
          if (libraryName != null &&
              libraryName.contains(IdeaXml.JUNIT) &&
              Comparing.strEqual(files[0], EclipseClasspathReader.getJunitClsUrl(libraryName.contains("4")))) {
            final Element orderEntry =
              addOrderEntry(EclipseXml.CON_KIND, EclipseXml.JUNIT_CONTAINER + "/" + libraryName.substring(IdeaXml.JUNIT.length()),
                            classpathRoot);
            setExported(orderEntry, libraryOrderEntry);
          }
          else {
            final String eclipseVariablePath = EclipseModuleManager.getInstance(libraryOrderEntry.getOwnerModule()).getEclipseVariablePath(files[0]);
            final Element orderEntry;
            if (eclipseVariablePath != null) {
              orderEntry = addOrderEntry(EclipseXml.VAR_KIND, eclipseVariablePath, classpathRoot);
            }
            else {
              orderEntry = addOrderEntry(EclipseXml.LIB_KIND, getRelativePath(files[0]), classpathRoot);
            }

            final String srcRelativePath;
            final String eclipseSrcVariablePath;

            final String[] srcFiles = libraryOrderEntry.getUrls(OrderRootType.SOURCES);
            if (srcFiles.length == 0) {
              srcRelativePath = null;
              eclipseSrcVariablePath = null;
            }
            else {
              final String lastSourceRoot = srcFiles[srcFiles.length - 1];
              srcRelativePath = getRelativePath(lastSourceRoot);
              eclipseSrcVariablePath = EclipseModuleManager.getInstance(libraryOrderEntry.getOwnerModule()).getEclipseSrcVariablePath(lastSourceRoot);
            }
            setOrRemoveAttribute(orderEntry, EclipseXml.SOURCEPATH_ATTR, eclipseSrcVariablePath != null ? eclipseSrcVariablePath : srcRelativePath);

            //clear javadocs before write new
            final List children = new ArrayList(orderEntry.getChildren(EclipseXml.ATTRIBUTES_TAG));
            for (Object o : children) {
              ((Element)o).detach();
            }
            final String[] docUrls = libraryOrderEntry.getUrls(JavadocOrderRootType.getInstance());
            for (final String docUrl : docUrls) {
              setJavadocPath(orderEntry, docUrl);
            }

            setExported(orderEntry, libraryOrderEntry);
          }
        }
      }
      else {
        final Element orderEntry;
        if (Comparing.strEqual(libraryName, IdeaXml.ECLIPSE_LIBRARY)) {
          orderEntry = addOrderEntry(EclipseXml.CON_KIND, EclipseXml.ECLIPSE_PLATFORM, classpathRoot);
        }
        else {
          orderEntry = addOrderEntry(EclipseXml.CON_KIND, EclipseXml.USER_LIBRARY + "/" + libraryName, classpathRoot);
        }
        setExported(orderEntry, libraryOrderEntry);
      }
    }
    else if (entry instanceof JdkOrderEntry) {
      if (entry instanceof InheritedJdkOrderEntry) {
        addOrderEntry(EclipseXml.CON_KIND, EclipseXml.JRE_CONTAINER, classpathRoot);
      }
      else {
        final Sdk jdk = ((JdkOrderEntry)entry).getJdk();
        String jdkLink;
        if (jdk == null) {
          jdkLink = EclipseXml.JRE_CONTAINER;
        }
        else {
          jdkLink = EclipseXml.JRE_CONTAINER;
          if (jdk.getSdkType() instanceof JavaSdkType) {
            jdkLink += EclipseXml.JAVA_SDK_TYPE;
          }
          jdkLink += "/" + jdk.getName();
        }
        addOrderEntry(EclipseXml.CON_KIND, jdkLink, classpathRoot);
      }
    }
    else {
      throw new ConversionException("Unknown EclipseProjectModel.ClasspathEntry: " + entry.getClass());
    }
  }

  private String getRelativePath(String url) {
    final Project project = myModel.getModule().getProject();
    final VirtualFile contentRoot = getContentRoot();
    final VirtualFile projectBaseDir = contentRoot != null ? contentRoot.getParent() : project.getBaseDir();
    assert projectBaseDir != null;
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file != null) {
      if (file.getFileSystem() instanceof JarFileSystem) {
        file = JarFileSystem.getInstance().getVirtualFileForJar(file);
      }
      assert file != null;
      if (contentRoot != null) {
        if (VfsUtil.isAncestor(contentRoot, file, false)) {
          return VfsUtil.getRelativePath(file, contentRoot, '/');
        } else {
          final String path = relativeToOtherModulePath(project, file);
          if (path != null) {
            return path;
          }
        }
      }
      if (VfsUtil.isAncestor(projectBaseDir, file, false)) {
        return "/" + VfsUtil.getRelativePath(file, projectBaseDir, '/');
      }
      else {
        return ProjectRootManagerImpl.extractLocalPath(url);
      }
    }
    else {
      if (contentRoot != null) {
        final String rootUrl = contentRoot.getUrl();
        if (url.startsWith(rootUrl) && url.length() > rootUrl.length()) {
          return url.substring(rootUrl.length() + 1); //without leading /
        }
      }
      final String projectUrl = projectBaseDir.getUrl();
      if (url.startsWith(projectUrl)) {
        return url.substring(projectUrl.length()); //leading /
      }

      return ProjectRootManagerImpl.extractLocalPath(url);
    }
  }

  @Nullable
  private static String relativeToOtherModulePath(Project project, VirtualFile file) {
    final Module module = ModuleUtil.findModuleForFile(file, project);
    if (module != null) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile otherRoot : contentRoots) {
        if (VfsUtil.isAncestor(otherRoot, file, false)) {
          return "/" + module.getName() + "/" + VfsUtil.getRelativePath(file, otherRoot, '/');
        }
      }
    }
    return null;
  }

  @Nullable
  private VirtualFile getContentRoot() {
    final ContentEntry[] entries = myModel.getContentEntries();
    if (entries.length > 0) {
      return entries[0].getFile();
    }
    return null;
  }

  private void setJavadocPath(final Element element, String javadocPath) {
    if (javadocPath != null) {
      Element child = new Element(EclipseXml.ATTRIBUTES_TAG);
      element.addContent(child);

      Element attrElement = child.getChild(EclipseXml.ATTRIBUTE_TAG);
      if (attrElement == null) {
        attrElement = new Element(EclipseXml.ATTRIBUTE_TAG);
        child.addContent(attrElement);
      }

      attrElement.setAttribute("name", "javadoc_location");

      final String protocol = VirtualFileManager.extractProtocol(javadocPath);
      if (!Comparing.strEqual(protocol, HttpFileSystem.getInstance().getProtocol())) {
        final String path = VfsUtil.urlToPath(javadocPath);
        final VirtualFile contentRoot = getContentRoot();
        final Project project = myModel.getModule().getProject();
        final VirtualFile baseDir = contentRoot != null ? contentRoot.getParent() : project.getBaseDir();
        if (Comparing.strEqual(protocol, JarFileSystem.getInstance().getProtocol())) {
          final VirtualFile javadocFile =
            JarFileSystem.getInstance().getVirtualFileForJar(VirtualFileManager.getInstance().findFileByUrl(javadocPath));
          if (javadocFile != null) {
            String relativeUrl = relativeToOtherModulePath(project, javadocFile);
            if (relativeUrl == null && VfsUtil.isAncestor(baseDir, javadocFile, false)) {
              relativeUrl = "/" + VfsUtil.getRelativePath(javadocFile, baseDir, '/');
            }
            if (relativeUrl != null) {
              if (javadocPath.indexOf(JarFileSystem.JAR_SEPARATOR) == -1) {
                javadocPath = StringUtil.trimEnd(javadocPath, "/") + JarFileSystem.JAR_SEPARATOR;
              }
              javadocPath = EclipseXml.JAR_PREFIX +
                            EclipseXml.PLATFORM_PROTOCOL +
                            "resource" +
                            relativeUrl +
                            javadocPath.substring(javadocFile.getUrl().length() - 1);
            } else {
              javadocPath = EclipseXml.JAR_PREFIX + EclipseXml.FILE_PROTOCOL + StringUtil.trimStart(path, "/");
            }
          }
          else {
            javadocPath = EclipseXml.JAR_PREFIX + EclipseXml.FILE_PROTOCOL + StringUtil.trimStart(path, "/");
          }
        }
        else if (new File(path).exists()) {
          javadocPath = EclipseXml.FILE_PROTOCOL + StringUtil.trimStart(path, "/");
        }
      }

      attrElement.setAttribute("value", javadocPath);
    }
  }

  private Element addOrderEntry(String kind, String path, Element classpathRoot) {
    final Element element = myOldEntries.get(kind + getJREKey(path));
    if (element != null){
      final Element clonedElement = (Element)element.clone();
      classpathRoot.addContent(clonedElement);
      return clonedElement;
    }
    Element orderEntry = new Element(EclipseXml.CLASSPATHENTRY_TAG);
    orderEntry.setAttribute(EclipseXml.KIND_ATTR, kind);
    if (path != null) {
      orderEntry.setAttribute(EclipseXml.PATH_ATTR, path);
    }
    classpathRoot.addContent(orderEntry);
    return orderEntry;
  }

  private static String getJREKey(String path) {
    return path.startsWith(EclipseXml.JRE_CONTAINER) ? EclipseXml.JRE_CONTAINER : path;
  }

  private static void setExported(Element orderEntry, ExportableOrderEntry dependency) {
    setOrRemoveAttribute(orderEntry, EclipseXml.EXPORTED_ATTR, dependency.isExported() ? EclipseXml.TRUE_VALUE : null);
  }

  private static void setOrRemoveAttribute(Element element, String name, String value) {
    if (value != null) {
      element.setAttribute(name, value);
    }
    else {
      element.removeAttribute(name);
    }
  }

  private static void setAttributeIfAbsent(Element element, String name, String value) {
    if (element.getAttribute(name) == null) {
      element.setAttribute(name, value);
    }
  }

}
