// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class EclipseUserLibrariesHelper {
  //private static final String ORG_ECLIPSE_JDT_CORE_PREFS = "org.eclipse.jdt.core.prefs";
  //private static final String ORG_ECLIPSE_JDT_CORE_USER_LIBRARY = "org.eclipse.jdt.core.userLibrary.";

  private EclipseUserLibrariesHelper() {
  }

  private static void writeUserLibrary(final Library library, final Element libElement) {
    final VirtualFile[] files = library.getFiles(OrderRootType.CLASSES);
    for (VirtualFile file : files) {
      Element archElement = new Element("archive");
      if (file.getFileSystem() instanceof JarFileSystem) {
        final VirtualFile localFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
        if (localFile != null) {
          file = localFile;
        }
      }
      archElement.setAttribute("path", file.getPath());
      libElement.addContent(archElement);
    }
  }

  public static void appendProjectLibraries(final Project project, @Nullable final File userLibrariesFile) throws IOException {
    if (userLibrariesFile == null) return;
    if (userLibrariesFile.exists() && !userLibrariesFile.isFile()) return;
    final File parentFile = userLibrariesFile.getParentFile();
    if (parentFile == null) return;
    if (!parentFile.isDirectory()) {
      if (!parentFile.mkdir()) return;
    }
    final Element userLibsElement = new Element("eclipse-userlibraries");
    final List<Library> libraries = new ArrayList<>(Arrays.asList(LibraryTablesRegistrar.getInstance().getLibraryTable(project).getLibraries()));
    ContainerUtil.addAll(libraries, LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraries());
    for (Library library : libraries) {
      Element libElement = new Element("library");
      libElement.setAttribute("name", library.getName());
      writeUserLibrary(library, libElement);
      userLibsElement.addContent(libElement);
    }
    JDOMUtil.writeDocument(new Document(userLibsElement), userLibrariesFile, "\n");
  }


  public static void readProjectLibrariesContent(@NotNull VirtualFile exportedFile, Project project, Collection<String> unknownLibraries)
    throws IOException, JDOMException {
    if (!exportedFile.isValid()) {
      return;
    }

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    Element element = JDOMUtil.load(exportedFile.getInputStream());
    WriteAction.run(() -> {
      for (Element libElement : element.getChildren("library")) {
        String libName = libElement.getAttributeValue("name");
        Library libraryByName = libraryTable.getLibraryByName(libName);
        if (libraryByName == null) {
          LibraryTable.ModifiableModel model = libraryTable.getModifiableModel();
          libraryByName = model.createLibrary(libName);
          model.commit();
        }
        if (libraryByName != null) {
          Library.ModifiableModel model = libraryByName.getModifiableModel();
          for (Element a : libElement.getChildren("archive")) {
            String rootPath = a.getAttributeValue("path");
            // IDEA-138039 Eclipse import: Unix file system: user library gets wrong paths
            LocalFileSystem fileSystem = LocalFileSystem.getInstance();
            VirtualFile localFile = fileSystem.findFileByPath(rootPath);
            if (rootPath.startsWith("/") && (localFile == null || !localFile.isValid())) {
              // relative to workspace root
              rootPath = project.getBasePath() + rootPath;
              localFile = fileSystem.findFileByPath(rootPath);
            }
            String url = localFile == null ? VfsUtilCore.pathToUrl(rootPath) : localFile.getUrl();
            if (localFile != null) {
              VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
              if (jarFile != null) {
                url = jarFile.getUrl();
              }
            }
            model.addRoot(url, OrderRootType.CLASSES);
          }
          model.commit();
        }
        unknownLibraries.remove(libName);  //ignore finally found libraries
      }
    });
  }
}
