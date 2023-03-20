// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.ConversionException;
import org.jetbrains.idea.eclipse.EclipseModuleManager;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseModuleManagerImpl;

import java.util.HashMap;
import java.util.Map;

public class EclipseClasspathWriter {
  public static final Logger LOG = Logger.getInstance(EclipseClasspathWriter.class);

  private final Map<String, Element> myOldEntries = new HashMap<>();

  @NotNull
  public Element writeClasspath(@NotNull ModuleRootModel model) {
    Element classpathElement = new Element(EclipseXml.CLASSPATH_TAG);
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
      addOrderEntry(EclipseXml.CON_KIND, support, classpathElement, eclipseModuleManager.getSrcPlace(support), myOldEntries);
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
          addOrderEntry(EclipseXml.SRC_KIND, relativePath, classpathRoot, shouldPlaceSeparately && index != -1 ? index : -1, myOldEntries);
        }
      }
    }
    else if (entry instanceof ModuleOrderEntry) {
      final String path = '/' + ((ModuleOrderEntry)entry).getModuleName();
      final Element oldElement = getOldElement(EclipseXml.SRC_KIND, path, myOldEntries);
      Element orderEntry = addOrderEntry(EclipseXml.SRC_KIND, path, classpathRoot);
      if (oldElement == null) {
        setAttributeIfAbsent(orderEntry, EclipseXml.COMBINEACCESSRULES_ATTR, EclipseXml.FALSE_VALUE);
      }
      setExported(orderEntry, ((ExportableOrderEntry)entry));
    }
    else if (entry instanceof LibraryOrderEntry libraryOrderEntry) {
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

            final String[] nativeRoots = libraryOrderEntry.getRootUrls(NativeLibraryOrderRootType.getInstance());
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
    return addOrderEntry(kind, path, classpathRoot, myOldEntries);
  }

  public static Element addOrderEntry(String kind, String path, Element classpathRoot, Map<String, Element> oldEntries) {
    return addOrderEntry(kind, path, classpathRoot, -1, oldEntries);
  }

  public static Element addOrderEntry(@NotNull String kind, String path, Element classpathRoot, int index, Map<String, Element> oldEntries) {
    Element element = getOldElement(kind, path, oldEntries);
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

  public static Element getOldElement(@NotNull String kind, String path, Map<String, Element> entries) {
    return entries.get(kind + getJREKey(path));
  }

  public static String getJREKey(String path) {
    return path.startsWith(EclipseXml.JRE_CONTAINER) ? EclipseXml.JRE_CONTAINER : path;
  }

  private static void setExported(Element orderEntry, ExportableOrderEntry dependency) {
    setExported(orderEntry, dependency.isExported());
  }

  public static void setExported(Element orderEntry, boolean exported) {
    setOrRemoveAttribute(orderEntry, EclipseXml.EXPORTED_ATTR, exported ? EclipseXml.TRUE_VALUE : null);
  }

  public static void setOrRemoveAttribute(@NotNull Element element, @NotNull String name, @Nullable String value) {
    if (value == null) {
      element.removeAttribute(name);
    }
    else {
      element.setAttribute(name, value);
    }
  }

  public static void setAttributeIfAbsent(@NotNull Element element, String name, String value) {
    if (element.getAttribute(name) == null) {
      element.setAttribute(name, value);
    }
  }
}
