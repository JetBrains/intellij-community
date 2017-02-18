/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.ConversionException;
import org.jetbrains.idea.eclipse.EclipseModuleManager;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseModuleManagerImpl;

import java.util.Map;

public class EclipseClasspathWriter {
  public static final Logger LOG = Logger.getInstance(EclipseClasspathWriter.class);

  private final Map<String, Element> myOldEntries = new THashMap<>();

  @NotNull
  public Element writeClasspath(@Nullable Element oldRoot, @NotNull ModuleRootModel model) {
    Element classpathElement = new Element(EclipseXml.CLASSPATH_TAG);
    if (oldRoot != null) {
      for (Element oldChild : oldRoot.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
        String oldKind = oldChild.getAttributeValue(EclipseXml.KIND_ATTR);
        String oldPath = oldChild.getAttributeValue(EclipseXml.PATH_ATTR);
        myOldEntries.put(oldKind + getJREKey(oldPath), oldChild);
      }
    }

    for (OrderEntry orderEntry : model.getOrderEntries()) {
      createClasspathEntry(orderEntry, classpathElement, model);
    }

    String outputPath = "bin";
    final String compilerOutputUrl = model.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputUrl();
    final EclipseModuleManager eclipseModuleManager = EclipseModuleManagerImpl.getInstance(model.getModule());
    final String linkedPath = eclipseModuleManager.getEclipseLinkedVarPath(compilerOutputUrl);
    if (linkedPath != null) {
      outputPath = linkedPath;
    }
    else {
      VirtualFile contentRoot = EPathUtil.getContentRoot(model);
      VirtualFile output = model.getModuleExtension(CompilerModuleExtension.class).getCompilerOutputPath();
      if (contentRoot != null && output != null && VfsUtilCore.isAncestor(contentRoot, output, false)) {
        outputPath = EPathUtil.collapse2EclipsePath(output.getUrl(), model);
      }
      else if (output == null && compilerOutputUrl != null) {
        outputPath = EPathUtil.collapse2EclipsePath(compilerOutputUrl, model);
      }
    }
    for (String support : eclipseModuleManager.getUsedCons()) {
      addOrderEntry(EclipseXml.CON_KIND, support, classpathElement, eclipseModuleManager.getSrcPlace(support));
    }
    setAttributeIfAbsent(addOrderEntry(EclipseXml.OUTPUT_KIND, outputPath, classpathElement), EclipseXml.PATH_ATTR, EclipseXml.BIN_DIR);

    return classpathElement;
  }

  private void createClasspathEntry(@NotNull OrderEntry entry, @NotNull Element classpathRoot, @NotNull final ModuleRootModel model) throws ConversionException {
    EclipseModuleManager eclipseModuleManager = EclipseModuleManagerImpl.getInstance(entry.getOwnerModule());
    if (entry instanceof ModuleSourceOrderEntry) {
      boolean shouldPlaceSeparately = eclipseModuleManager.isExpectedModuleSourcePlace(ArrayUtil.find(model.getOrderEntries(), entry));
      for (ContentEntry contentEntry : model.getContentEntries()) {
        VirtualFile contentRoot = contentEntry.getFile();
        for (SourceFolder sourceFolder : contentEntry.getSourceFolders()) {
          String srcUrl = sourceFolder.getUrl();
          String relativePath = EPathUtil.collapse2EclipsePath(srcUrl, model);
          if (!Comparing.equal(contentRoot, EPathUtil.getContentRoot(model))) {
            String linkedPath = EclipseModuleManagerImpl.getInstance(entry.getOwnerModule()).getEclipseLinkedSrcVariablePath(srcUrl);
            if (linkedPath != null) {
              relativePath = linkedPath;
            }
          }
          int index = eclipseModuleManager.getSrcPlace(srcUrl);
          addOrderEntry(EclipseXml.SRC_KIND, relativePath, classpathRoot, shouldPlaceSeparately && index != -1 ? index : -1);
        }
      }
    }
    else if (entry instanceof ModuleOrderEntry) {
      final String path = '/' + ((ModuleOrderEntry)entry).getModuleName();
      final Element oldElement = getOldElement(EclipseXml.SRC_KIND, path);
      Element orderEntry = addOrderEntry(EclipseXml.SRC_KIND, path, classpathRoot);
      if (oldElement == null) {
        setAttributeIfAbsent(orderEntry, EclipseXml.COMBINEACCESSRULES_ATTR, EclipseXml.FALSE_VALUE);
      }
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
              orderEntry = addOrderEntry(EclipseXml.LIB_KIND, EPathUtil.collapse2EclipsePath(files[0], model), classpathRoot);
            }

            final String srcRelativePath;
            String eclipseSrcVariablePath = null;

            boolean addSrcRoots = true;
            String[] srcFiles = libraryOrderEntry.getRootUrls(OrderRootType.SOURCES);
            if (srcFiles.length == 0) {
              srcRelativePath = null;
            }
            else {
              final String srcFile = srcFiles[0];
              srcRelativePath = EPathUtil.collapse2EclipsePath(srcFile, model);
              if (eclipseVariablePath != null) {
                eclipseSrcVariablePath = eclipseModuleManager.getEclipseSrcVariablePath(srcFile);
                if (eclipseSrcVariablePath == null) {
                  eclipseSrcVariablePath = eclipseModuleManager.getEclipseLinkedSrcVariablePath(srcFile);
                }
                if (eclipseSrcVariablePath == null) {
                  eclipseSrcVariablePath = EPathUtil.collapse2EclipseVariabledPath(libraryOrderEntry, OrderRootType.SOURCES);
                  if (eclipseSrcVariablePath != null) {
                    eclipseSrcVariablePath = "/" + eclipseSrcVariablePath;
                  }
                  else {
                    if (newVarLibrary) { //new library which cannot be replaced with vars
                      orderEntry.detach();
                      orderEntry = addOrderEntry(EclipseXml.LIB_KIND, EPathUtil.collapse2EclipsePath(files[0], model), classpathRoot);
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

            EJavadocUtil.setupJavadocAttributes(orderEntry, libraryOrderEntry, model);

            final String[] nativeRoots = libraryOrderEntry.getUrls(NativeLibraryOrderRootType.getInstance());
            if (nativeRoots.length > 0) {
              EJavadocUtil.setupAttributes(orderEntry, nativeRoot -> EPathUtil.collapse2EclipsePath(nativeRoot, model), EclipseXml.DLL_LINK, nativeRoots);
            }
            setExported(orderEntry, libraryOrderEntry);
          }
        }
      }
      else {
        Element orderEntry;
        if (eclipseModuleManager.getUnknownCons().contains(libraryName)) {
          orderEntry = addOrderEntry(EclipseXml.CON_KIND, libraryName, classpathRoot);
        }
        else if (Comparing.strEqual(libraryName, IdeaXml.ECLIPSE_LIBRARY)) {
          orderEntry = addOrderEntry(EclipseXml.CON_KIND, EclipseXml.ECLIPSE_PLATFORM, classpathRoot);
        }
        else {
          orderEntry = addOrderEntry(EclipseXml.CON_KIND, EclipseXml.USER_LIBRARY + '/' + libraryName, classpathRoot);
        }
        setExported(orderEntry, libraryOrderEntry);
      }
    }
    else if (entry instanceof JdkOrderEntry) {
      if (entry instanceof InheritedJdkOrderEntry) {
        if (!EclipseModuleManagerImpl.getInstance(entry.getOwnerModule()).isForceConfigureJDK()) {
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
          jdkLink += '/' + jdk.getName();
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

  private Element addOrderEntry(@NotNull String kind, String path, Element classpathRoot, int index) {
    Element element = getOldElement(kind, path);
    if (element != null) {
      Element clonedElement = element.clone();
      if (index == -1 || index >= classpathRoot.getContentSize()) {
        classpathRoot.addContent(clonedElement);
      }
      else {
        classpathRoot.addContent(index, clonedElement);
      }
      return clonedElement;
    }

    Element orderEntry = new Element(EclipseXml.CLASSPATHENTRY_TAG);
    orderEntry.setAttribute(EclipseXml.KIND_ATTR, kind);
    if (path != null) {
      orderEntry.setAttribute(EclipseXml.PATH_ATTR, path);
    }
    if (index == -1 || index >= classpathRoot.getContentSize()) {
      classpathRoot.addContent(orderEntry);
    }
    else {
      classpathRoot.addContent(index, orderEntry);
    }
    return orderEntry;
  }

  private Element getOldElement(@NotNull String kind, String path) {
    return myOldEntries.get(kind + getJREKey(path));
  }

  private static String getJREKey(String path) {
    return path.startsWith(EclipseXml.JRE_CONTAINER) ? EclipseXml.JRE_CONTAINER : path;
  }

  private static void setExported(Element orderEntry, ExportableOrderEntry dependency) {
    setOrRemoveAttribute(orderEntry, EclipseXml.EXPORTED_ATTR, dependency.isExported() ? EclipseXml.TRUE_VALUE : null);
  }

  private static void setOrRemoveAttribute(@NotNull Element element, @NotNull String name, @Nullable String value) {
    if (value == null) {
      element.removeAttribute(name);
    }
    else {
      element.setAttribute(name, value);
    }
  }

  private static void setAttributeIfAbsent(@NotNull Element element, String name, String value) {
    if (element.getAttribute(name) == null) {
      element.setAttribute(name, value);
    }
  }
}
