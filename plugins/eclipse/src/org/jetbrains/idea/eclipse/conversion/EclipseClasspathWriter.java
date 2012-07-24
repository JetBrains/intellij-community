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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseModuleManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class EclipseClasspathWriter {
  private static final Logger LOG = Logger.getInstance("#" + EclipseClasspathWriter.class.getName());
  private final ModuleRootModel myModel;
  private final Map<String, Element> myOldEntries = new HashMap<String, Element>();

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
    final String compilerOutputUrl = myModel.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputUrl();
    final EclipseModuleManager eclipseModuleManager = EclipseModuleManager.getInstance(myModel.getModule());
    final String linkedPath = eclipseModuleManager.getEclipseLinkedVarPath(compilerOutputUrl);
    if (linkedPath != null) {
      outputPath = linkedPath;
    } else {
      final VirtualFile contentRoot = EPathUtil.getContentRoot(myModel);
      final VirtualFile output = myModel.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputPath();
      if (contentRoot != null && output != null && VfsUtil.isAncestor(contentRoot, output, false)) {
        outputPath = EPathUtil.collapse2EclipsePath(output.getUrl(), myModel);
      }
      else if (output == null && compilerOutputUrl != null) {
        outputPath = EPathUtil.collapse2EclipsePath(compilerOutputUrl, myModel);
      }
    }
    if (eclipseModuleManager.isGroovyDslSupport()) {
      final Integer place = eclipseModuleManager.getSrcPlace(EclipseXml.GROOVY_DSL_CONTAINER);
      addOrderEntry(EclipseXml.CON_KIND, EclipseXml.GROOVY_DSL_CONTAINER, classpathElement, place != null ? place.intValue() : -1);
    }
    final Element orderEntry = addOrderEntry(EclipseXml.OUTPUT_KIND, outputPath, classpathElement);
    setAttributeIfAbsent(orderEntry, EclipseXml.PATH_ATTR, EclipseXml.BIN_DIR);
  }

  private void createClasspathEntry(OrderEntry entry, Element classpathRoot) throws ConversionException {
    final EclipseModuleManager eclipseModuleManager = EclipseModuleManager.getInstance(entry.getOwnerModule());
    if (entry instanceof ModuleSourceOrderEntry) {
      final boolean shouldPlaceSeparately =
        eclipseModuleManager.isExpectedModuleSourcePlace(Arrays.binarySearch(myModel.getOrderEntries(), entry));
      final ContentEntry[] entries = myModel.getContentEntries();
      for (final ContentEntry contentEntry : entries) {
        final VirtualFile contentRoot = contentEntry.getFile();
        for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          final String srcUrl = sourceFolder.getUrl();
          String relativePath = EPathUtil.collapse2EclipsePath(srcUrl, myModel);
          if (!Comparing.equal(contentRoot, EPathUtil.getContentRoot(myModel))) {
            final String linkedPath = EclipseModuleManager.getInstance(entry.getOwnerModule()).getEclipseLinkedSrcVariablePath(srcUrl);
            if (linkedPath != null) {
              relativePath = linkedPath;
            }
          }
          final Integer idx = eclipseModuleManager.getSrcPlace(srcUrl);
          addOrderEntry(EclipseXml.SRC_KIND, relativePath, classpathRoot, shouldPlaceSeparately && idx != null ? idx.intValue() : -1);
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
        final String[] files = libraryOrderEntry.getRootUrls(OrderRootType.CLASSES);
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
            boolean newVarLibrary = false;

            boolean link = false;
            String eclipseVariablePath = eclipseModuleManager.getEclipseVariablePath(files[0]);
            if (eclipseVariablePath == null) {
              eclipseVariablePath = eclipseModuleManager.getEclipseLinkedVarPath(files[0]);
              link = eclipseVariablePath != null;
            }

            if (eclipseVariablePath == null && !eclipseModuleManager.isEclipseLibUrl(files[0])) { //new library was added
              newVarLibrary = true;
              eclipseVariablePath = EPathUtil.collapse2EclipseVariabledPath(libraryOrderEntry, OrderRootType.CLASSES);
            }
            Element orderEntry;
            if (eclipseVariablePath != null) {
              orderEntry = addOrderEntry(link ? EclipseXml.LIB_KIND : EclipseXml.VAR_KIND, eclipseVariablePath, classpathRoot);
            }
            else {
              LOG.assertTrue(!StringUtil.isEmptyOrSpaces(files[0]), "Library: " + libraryName);
              orderEntry = addOrderEntry(EclipseXml.LIB_KIND, EPathUtil.collapse2EclipsePath(files[0], myModel), classpathRoot);
            }

            final String srcRelativePath;
            String eclipseSrcVariablePath = null;

            boolean addSrcRoots = true;
            final String[] srcFiles = libraryOrderEntry.getRootUrls(OrderRootType.SOURCES);
            if (srcFiles.length == 0) {
              srcRelativePath = null;
            }
            else {
              final String srcFile = srcFiles[0];
              srcRelativePath = EPathUtil.collapse2EclipsePath(srcFile, myModel);
              if (eclipseVariablePath != null) {
                eclipseSrcVariablePath = eclipseModuleManager.getEclipseSrcVariablePath(srcFile);
                if (eclipseSrcVariablePath == null) {
                  eclipseSrcVariablePath = eclipseModuleManager.getEclipseLinkedSrcVariablePath(srcFile);
                }
                if (eclipseSrcVariablePath == null) {
                  eclipseSrcVariablePath = EPathUtil.collapse2EclipseVariabledPath(libraryOrderEntry, OrderRootType.SOURCES);
                  if (eclipseSrcVariablePath != null) {
                    eclipseSrcVariablePath = "/" + eclipseSrcVariablePath;
                  } else {
                    if (newVarLibrary) { //new library which cannot be replaced with vars
                      orderEntry.detach();
                      orderEntry = addOrderEntry(EclipseXml.LIB_KIND, EPathUtil.collapse2EclipsePath(files[0], myModel), classpathRoot);
                    }
                    else {
                      LOG.info("Added root " + srcRelativePath + " (in existing var library) can't be replaced with any variable; src roots placed in .eml only");
                      addSrcRoots = false;
                    }
                  }
                }
              }
            }
            setOrRemoveAttribute(orderEntry, EclipseXml.SOURCEPATH_ATTR, addSrcRoots ? (eclipseSrcVariablePath != null ? eclipseSrcVariablePath : srcRelativePath) : null);

            EJavadocUtil.setupJavadocAttributes(orderEntry, libraryOrderEntry, myModel);
            setExported(orderEntry, libraryOrderEntry);
          }
        }
      }
      else {
        final Element orderEntry;
        if (eclipseModuleManager.getUnknownCons().contains(libraryName)) {
          orderEntry = addOrderEntry(EclipseXml.CON_KIND, libraryName, classpathRoot);
        } else if (Comparing.strEqual(libraryName, IdeaXml.ECLIPSE_LIBRARY)) {
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
        if (!EclipseModuleManager.getInstance(entry.getOwnerModule()).isForceConfigureJDK()) {
          addOrderEntry(EclipseXml.CON_KIND, EclipseXml.JRE_CONTAINER, classpathRoot);
        }
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

  private Element addOrderEntry(String kind, String path, Element classpathRoot) {
    return addOrderEntry(kind, path, classpathRoot, -1);
  }

  private Element addOrderEntry(String kind, String path, Element classpathRoot, int idx) {
    final Element element = myOldEntries.get(kind + getJREKey(path));
    if (element != null){
      final Element clonedElement = (Element)element.clone();
      if (idx == -1 || idx >= classpathRoot.getContentSize()) {
        classpathRoot.addContent(clonedElement);
      } else {
        classpathRoot.addContent(idx, clonedElement);
      }
      return clonedElement;
    }
    Element orderEntry = new Element(EclipseXml.CLASSPATHENTRY_TAG);
    orderEntry.setAttribute(EclipseXml.KIND_ATTR, kind);
    if (path != null) {
      orderEntry.setAttribute(EclipseXml.PATH_ATTR, path);
    }
    if (idx == -1) {
      classpathRoot.addContent(orderEntry);
    } else {
      classpathRoot.addContent(idx, orderEntry);
    }
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
