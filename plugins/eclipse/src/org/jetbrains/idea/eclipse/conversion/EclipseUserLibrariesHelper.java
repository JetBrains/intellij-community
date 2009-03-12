/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/*
 * User: anna
 * Date: 11-Mar-2009
 */
package org.jetbrains.idea.eclipse.conversion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Collection;

public class EclipseUserLibrariesHelper {
  private static final String ORG_ECLIPSE_JDT_CORE_PREFS = "org.eclipse.jdt.core.prefs";
  private static final String ORG_ECLIPSE_JDT_CORE_USER_LIBRARY = "org.eclipse.jdt.core.userLibrary.";

  private EclipseUserLibrariesHelper() {
  }

  private static String writeUserLibrary(Library library, Project project) {
    StringBuffer buf = new StringBuffer();
    buf.append("<?xml version\\=\"1.0\" encoding\\=\"UTF-8\"?>\\r\\n<userlibrary systemlibrary\\=\"false\" version\\=\"1\">");
    final String[] urls = library.getUrls(OrderRootType.CLASSES);  //todo remove existing

    for (String url : urls) {
      buf.append("\\r\\n\\t<archive path\\=\"").append(EclipseClasspathWriter.getRelativePath(url, new String[0], true, project, null))
        .append("\">");
      /*  "\\r\\n\\t\\t<attributes>\\r\\n\\t\\t\\t" +
      "<attribute name\\=\"javadoc_location\" value\\=\"file\\:/C\\:/conf\"/>\\r\\n\\t\\t" +
      "</attributes>";*/
      buf.append("\\r\\n\\t</archive>");
    }
    buf.append("\\r\\n</userlibrary>\\r\\n");
    return buf.toString();
  }

  public static void appendProjectLibraries(final Project project, final File workspaceRoot) throws IOException {
    final File prefsParent = getPathToUserLibsFile(workspaceRoot);
    if (!prefsParent.isDirectory()) {
      if (!prefsParent.mkdirs()) return;
    }
    File prefs = new File(prefsParent, ORG_ECLIPSE_JDT_CORE_PREFS);
    if (!prefs.exists()) {
      if (!prefs.createNewFile()) return;
    }

    final Properties properties = new Properties();
    final FileInputStream inputStream = new FileInputStream(prefs);
    try {
      properties.load(inputStream);
    }
    catch (IOException e) {
      inputStream.close();
    }

    final Library[] libraries = ProjectLibraryTable.getInstance(project).getLibraries();
    for (Library library : libraries) {
      properties.setProperty(ORG_ECLIPSE_JDT_CORE_USER_LIBRARY + library.getName(), writeUserLibrary(library, project));
    }

    FileOutputStream outputStream = new FileOutputStream(prefs);
    try {
      properties.save(outputStream, null);
    }
    finally {
      outputStream.close();
    }
  }

  private static File getPathToUserLibsFile(File workspaceRoot) {
    return new File(new File(new File(new File(workspaceRoot, ".metadata"), ".plugins"), "org.eclipse.core.runtime"), ".settings");
  }

  public static void readProjectLibrariesContent(File workspace, Project project, Collection<String> unknownLibraries) throws IOException, JDOMException {
    final File parentPrefs = getPathToUserLibsFile(workspace);
    final LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
    if (parentPrefs.isDirectory()) {
      final File prefs = new File(parentPrefs, ORG_ECLIPSE_JDT_CORE_PREFS);
      if (prefs.exists()) {
        final Properties properties = new Properties();
        FileInputStream inputStream = new FileInputStream(prefs);
        try {
          properties.load(inputStream);
        }
        finally {
          inputStream.close();
        }
        for (Object prop : properties.keySet()) {
          if (((String)prop).startsWith(ORG_ECLIPSE_JDT_CORE_USER_LIBRARY)) {
            final String libName = ((String)prop).substring(ORG_ECLIPSE_JDT_CORE_USER_LIBRARY.length());
            Library libraryByName = libraryTable.getLibraryByName(libName);
            if (libraryByName == null) {
              final LibraryTable.ModifiableModel model = libraryTable.getModifiableModel();
              libraryByName = model.createLibrary(libName);
              model.commit();
            }
            if (libraryByName != null) {
              final Library.ModifiableModel model = libraryByName.getModifiableModel();
              final String libDescriptor = properties.getProperty((String)prop);
              final Document document = JDOMUtil.loadDocument(libDescriptor);
              for (Object o : document.getRootElement().getChildren("archive")) {
                String rootPath = ((Element)o).getAttributeValue("path");
                if (rootPath.startsWith("/")) { //relative to workspace root
                  rootPath = project.getBaseDir().getPath() + rootPath;
                }
                String url = VfsUtil.pathToUrl(rootPath);
                final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(url);
                if (localFile != null) {
                  final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
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
        }
      }
    }
  }
}