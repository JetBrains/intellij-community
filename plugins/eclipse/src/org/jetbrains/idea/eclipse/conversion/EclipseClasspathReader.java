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

import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.util.ErrorLog;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class EclipseClasspathReader {
  private final String myRootPath;
  private final Project myProject;
  @Nullable private final List<String> myCurrentRoots;
  private ContentEntry myContentEntry;

  public EclipseClasspathReader(final String rootPath, final Project project, @Nullable List<String> currentRoots) {
    myRootPath = rootPath;
    myProject = project;
    myCurrentRoots = currentRoots;
  }

  public void init(ModifiableRootModel model) {
    myContentEntry = model.addContentEntry(VfsUtil.pathToUrl(myRootPath));
    model.inheritSdk();
  }

  public static void collectVariables(Set<String> usedVariables, Element classpathElement) {
    for (Object o : classpathElement.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
      final Element element = (Element)o;
      final String kind = element.getAttributeValue(EclipseXml.KIND_ATTR);
      if (Comparing.strEqual(kind, EclipseXml.VAR_KIND)) {
        String path = element.getAttributeValue(EclipseXml.PATH_ATTR);
        if (path == null) continue;
        int slash = path.indexOf("/");
        if (slash > 0) {
          usedVariables.add(path.substring(0, slash));
        }
        else {
          usedVariables.add(path);
        }


        final String srcPath = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
        if (srcPath == null) continue;
        final int varStart = srcPath.startsWith("/") ? 1 : 0;
        final int slash2 = srcPath.indexOf("/", varStart);
        if (slash2 > 0) {
          usedVariables.add(srcPath.substring(varStart, slash2));
        }
        else {
          usedVariables.add(srcPath.substring(varStart));
        }
      }
    }
  }

  public void readClasspath(ModifiableRootModel model,
                            final Collection<String> unknownLibraries,
                            Collection<String> unknownJdks,
                            final Set<String> usedVariables,
                            Set<String> refsToModules,
                            final String testPattern,
                            Element classpathElement) throws IOException, ConversionException {
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (!(orderEntry instanceof ModuleSourceOrderEntry)) {
        model.removeOrderEntry(orderEntry);
      }
    }
    for (Object o : classpathElement.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
      try {
        readClasspathEntry(model, unknownLibraries, unknownJdks, usedVariables, refsToModules, testPattern, (Element)o);
      }
      catch (ConversionException e) {
        ErrorLog.rethrow(ErrorLog.Level.Warning, null, EclipseXml.CLASSPATH_FILE, e);
      }
    }
  }

  private void readClasspathEntry(ModifiableRootModel rootModel,
                                  final Collection<String> unknownLibraries,
                                  Collection<String> unknownJdks,
                                  final Set<String> usedVariables,
                                  Set<String> refsToModules,
                                  final String testPattern,
                                  Element element) throws ConversionException {
    String kind = element.getAttributeValue(EclipseXml.KIND_ATTR);
    if (kind == null) {
      throw new ConversionException("Missing classpathentry/@kind");
    }


    String path = element.getAttributeValue(EclipseXml.PATH_ATTR);
    if (path == null) {
      throw new ConversionException("Missing classpathentry/@path");
    }

    final boolean exported = EclipseXml.TRUE_VALUE.equals(element.getAttributeValue(EclipseXml.EXPORTED_ATTR));

    if (kind.equals(EclipseXml.SRC_KIND)) {
      if (path.startsWith("/")) {
        final String moduleName = path.substring(1);
        refsToModules.add(moduleName);
        rootModel.addInvalidModuleEntry(moduleName).setExported(exported);
      }
      else {
        getContentEntry().addSourceFolder(VfsUtil.pathToUrl(myRootPath + "/" + path), testPattern != null && testPattern.length() > 0 && path.matches(testPattern));
      }
    }

    else if (kind.equals(EclipseXml.OUTPUT_KIND)) {
      final CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
      compilerModuleExtension.setCompilerOutputPath(VfsUtil.pathToUrl(myRootPath + "/" + path));
      compilerModuleExtension.inheritCompilerOutputPath(false);
    }

    else if (kind.equals(EclipseXml.LIB_KIND)) {
      final String libName = getPresentableName(path);
      final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(libName);
      final Library.ModifiableModel modifiableModel = library.getModifiableModel();

      modifiableModel.addRoot(getUrl(path), OrderRootType.CLASSES);

      final String sourcePath = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
      if (sourcePath != null) {
        modifiableModel.addRoot(getUrl(sourcePath), OrderRootType.SOURCES);
      }

      final List<String> docPaths = getJavadocAttribute(element);
      if (docPaths != null) {
        for (String docPath : docPaths) {
          modifiableModel.addRoot(docPath, JavadocOrderRootType.getInstance());
        }
      }

      modifiableModel.commit();

      setLibraryEntryExported(rootModel, exported, library);
    }
    else if (kind.equals(EclipseXml.VAR_KIND)) {
      int slash = path.indexOf("/");
      if (slash == 0) {
        throw new ConversionException("Incorrect 'classpathentry/var@path' format");
      }

      final String libName = getPresentableName(path);
      final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(libName);
      final Library.ModifiableModel modifiableModel = library.getModifiableModel();


      final String clsVar;
      final String clsPath;
      if (slash > 0) {
        clsVar = path.substring(0, slash);
        clsPath = path.substring(slash + 1);
      }
      else {
        clsVar = path;
        clsPath = null;
      }
      usedVariables.add(clsVar);
      final String url = PathMacroManager.getInstance(rootModel.getModule()).expandPath(getVariableRelatedPath(clsVar, clsPath));
      modifiableModel.addRoot(getUrl(url), OrderRootType.CLASSES);

      final String srcPathAttr = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
      if (srcPathAttr != null) {
        final String srcVar;
        final String srcPath;
        final int varStart = srcPathAttr.startsWith("/") ? 1 : 0;

        int slash2 = srcPathAttr.indexOf("/", varStart);
        if (slash2 > 0) {
          srcVar = srcPathAttr.substring(varStart, slash2);
          srcPath = srcPathAttr.substring(slash2 + 1);
        }
        else {
          srcVar = srcPathAttr.substring(varStart);
          srcPath = null;
        }
        usedVariables.add(srcVar);
        final String srcUrl = PathMacroManager.getInstance(rootModel.getModule()).expandPath(getVariableRelatedPath(srcVar, srcPath));
        modifiableModel.addRoot(getUrl(srcUrl), OrderRootType.SOURCES);
      }

      final List<String> docPaths = getJavadocAttribute(element);
      if (docPaths != null) {
        for (String docPath : docPaths) {
          modifiableModel.addRoot(docPath, JavadocOrderRootType.getInstance());
        }
      }

      modifiableModel.commit();

      setLibraryEntryExported(rootModel, exported, library);
    }
    else if (kind.equals(EclipseXml.CON_KIND)) {
      if (path.equals(EclipseXml.ECLIPSE_PLATFORM)) {
        addNamedLibrary(rootModel, unknownLibraries, exported, IdeaXml.ECLIPSE_LIBRARY, LibraryTablesRegistrar.APPLICATION_LEVEL);
      }
      else if (path.startsWith(EclipseXml.JRE_CONTAINER)) {

        final String jdkName = getLastPathComponent(path);
        if (jdkName == null) {
          rootModel.inheritSdk();
        }
        else {
          final Sdk moduleJdk = ProjectJdkTable.getInstance().findJdk(jdkName);
          if (moduleJdk != null) {
            rootModel.setSdk(moduleJdk);
          }
          else {
            rootModel.setInvalidSdk(jdkName, IdeaXml.JAVA_SDK_TYPE);
            unknownJdks.add(jdkName);
          }
        }
        OrderEntry[] orderEntries = rootModel.getOrderEntries();
        orderEntries = ArrayUtil.append(orderEntries, orderEntries[0]);
        rootModel.rearrangeOrderEntries(ArrayUtil.remove(orderEntries, 0));
      }
      else if (path.startsWith(EclipseXml.USER_LIBRARY)) {
        addNamedLibrary(rootModel, unknownLibraries, exported, getPresentableName(path), LibraryTablesRegistrar.PROJECT_LEVEL);
      }
      else if (path.startsWith(EclipseXml.JUNIT_CONTAINER)) {
        final String junitName = IdeaXml.JUNIT + getPresentableName(path);
        final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(junitName);
        final Library.ModifiableModel modifiableModel = library.getModifiableModel();
        modifiableModel.addRoot(getJunitClsUrl(junitName.contains("4")), OrderRootType.CLASSES);
        modifiableModel.commit();
      }
    }
    else {
      throw new ConversionException("Unknown classpathentry/@kind: " + kind);
    }
  }

  private static void setLibraryEntryExported(ModifiableRootModel rootModel, boolean exported, Library library) {
    for (OrderEntry orderEntry : rootModel.getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry &&
          ((LibraryOrderEntry)orderEntry).isModuleLevel() &&
          Comparing.equal(((LibraryOrderEntry)orderEntry).getLibrary(), library)) {
        ((LibraryOrderEntry)orderEntry).setExported(exported);
        break;
      }
    }
  }

  private void addNamedLibrary(final ModifiableRootModel rootModel,
                               final Collection<String> unknownLibraries,
                               final boolean exported,
                               final String name,
                               final String notFoundLibraryLevel) {
    final LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();
    Library lib = tablesRegistrar.getLibraryTable().getLibraryByName(name);
    if (lib == null) {
      lib = tablesRegistrar.getLibraryTable(myProject).getLibraryByName(name);
    }
    if (lib == null) {
      for (LibraryTable table : tablesRegistrar.getCustomLibraryTables()) {
        lib = table.getLibraryByName(name);
        if (lib != null) {
          break;
        }
      }
    }
    if (lib != null) {
      rootModel.addLibraryEntry(lib).setExported(exported);
    }
    else {
      unknownLibraries.add(name);
      rootModel.addInvalidLibrary(name, notFoundLibraryLevel).setExported(exported);
    }
  }

  @NotNull
  private static String getPresentableName(@NotNull String path) {
    final String pathComponent = getLastPathComponent(path);
    return pathComponent != null ? pathComponent : path;
  }

  @Nullable
  public static String getLastPathComponent(final String path) {
    final int idx = path.lastIndexOf('/');
    return idx < 0 || idx == path.length() - 1 ? null : path.substring(idx + 1);
  }

  private ContentEntry getContentEntry() {
    return myContentEntry;
  }

  private static String getVariableRelatedPath(String var, String path) {
    return var == null ? null : ("$" + var + "$" + (path == null ? "" : ("/" + path)));
  }

  private String getUrl(final String path) {
    String url = null;
    if (path.startsWith("/")) {
      final String relativePath = new File(myRootPath).getParent() + "/" + path;
      final File file = new File(relativePath);
      if (file.exists()) {
        url = VfsUtil.pathToUrl(relativePath);
      } else if (new File(path).exists()) {
        url = VfsUtil.pathToUrl(path);
      }
      else {
        final String rootPath = getRootPath(path);
        final String relativeToRootPath = getRelativeToRootPath(path);

        final Module otherModule = ModuleManager.getInstance(myProject).findModuleByName(rootPath);
        if (otherModule != null) {
          url = relativeToOtherModule(otherModule, relativeToRootPath);
        }
        else if (myCurrentRoots != null) {
          url = relativeToContentRoots(myCurrentRoots, rootPath, relativeToRootPath);
        }
      }
    }
    if (url == null) {
      final String absPath = myRootPath + "/" + path;
      if (new File(absPath).exists()) {
        url = VfsUtil.pathToUrl(absPath);
      }
      else {
        url = VfsUtil.pathToUrl(path);
      }
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

  /**
   * @param path path in format /module_root/relative_path
   * @return module_root
   */
  @NotNull
  private static String getRootPath(String path) {
    int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx > 1 ? path.substring(1, secondSlIdx) : path.substring(1);
  }

  /**
   * @param path path in format /module_root/relative_path
   * @return relative_path or null if /module_root
   */
  @Nullable
  private static String getRelativeToRootPath(String path) {
    final int secondSlIdx = path.indexOf('/', 1);
    return secondSlIdx != -1 && secondSlIdx + 1 < path.length() ? path.substring(secondSlIdx + 1) : null;
  }

  @Nullable
  private static String relativeToContentRoots(final @NotNull List<String> currentRoots,
                                               final @NotNull String rootPath,
                                               final @Nullable String relativeToRootPath) {
    for (String currentRoot : currentRoots) {
      if (currentRoot.endsWith(rootPath)) { //rootPath = content_root <=> applicable root: abs_path/content_root
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

  @Nullable
  private static String relativeToOtherModule(final @NotNull Module otherModule, final @Nullable String relativeToOtherModule) {
    final VirtualFile[] contentRoots = ModuleRootManager.getInstance(otherModule).getContentRoots();
    for (VirtualFile contentRoot : contentRoots) {
      if (relativeToOtherModule == null) {
        return contentRoot.getUrl();
      }
      final File relativeToOtherModuleFile = new File(contentRoot.getPath(), relativeToOtherModule);
      if (relativeToOtherModuleFile.exists()) {
        return VfsUtil.pathToUrl(relativeToOtherModuleFile.getPath());
      }
    }
    return null;
  }

  @Nullable
  private List<String> getJavadocAttribute(Element element) {
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
          javadocPath = javadocPath.replaceFirst(EclipseXml.FILE_PROTOCOL, EclipseXml.FILE_PROTOCOL + "/");
        }
        if (javadocPath.startsWith(EclipseXml.FILE_PROTOCOL) &&
            new File(javadocPath.substring(EclipseXml.FILE_PROTOCOL.length())).exists()) {
          result.add(VfsUtil.pathToUrl(javadocPath.substring(EclipseXml.FILE_PROTOCOL.length())));
        }
        else {

          final String protocol = VirtualFileManager.extractProtocol(javadocPath);
          if (Comparing.strEqual(protocol, HttpFileSystem.getInstance().getProtocol())) {
            result.add(javadocPath);
          }
          else if (javadocPath.startsWith(EclipseXml.JAR_PREFIX)) {
            final String jarJavadocPath = javadocPath.substring(EclipseXml.JAR_PREFIX.length());
            if (jarJavadocPath.startsWith(EclipseXml.PLATFORM_PROTOCOL)) {
              String relativeToPlatform = jarJavadocPath.substring(EclipseXml.PLATFORM_PROTOCOL.length() + "resources".length());
              result
                .add(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, new File(myRootPath).getParent() + "/" + relativeToPlatform));
            }
            else if (jarJavadocPath.startsWith(EclipseXml.FILE_PROTOCOL)) {
              result
                .add(VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, jarJavadocPath.substring(EclipseXml.FILE_PROTOCOL.length())));
            }
            else {
              result.add(javadocPath);
            }
          }
        }
      }
    }
    return result;
  }

  static String getJunitClsUrl(final boolean version4) {
    String url = version4 ? JavaSdkUtil.getJunit4JarPath() : JavaSdkUtil.getJunit3JarPath();
    final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.pathToUrl(url));
    if (localFile != null) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
      url = jarFile != null ? jarFile.getUrl() : localFile.getUrl();
    }
    return url;
  }

  public static void readIDEASpecific(final Element root, ModifiableRootModel model) throws InvalidDataException {
    PathMacroManager.getInstance(model.getModule()).expandPaths(root);

    model.getModuleExtension(LanguageLevelModuleExtension.class).readExternal(root);

    final CompilerModuleExtension compilerModuleExtension = model.getModuleExtension(CompilerModuleExtension.class);
    final Element testOutputElement = root.getChild(IdeaXml.OUTPUT_TEST_TAG);
    if (testOutputElement != null) {
      compilerModuleExtension.setCompilerOutputPathForTests(testOutputElement.getAttributeValue(IdeaXml.URL_ATTR));
    }

    final String inheritedOutput = root.getAttributeValue(IdeaXml.INHERIT_COMPILER_OUTPUT_ATTR);
    if (inheritedOutput != null && Boolean.valueOf(inheritedOutput).booleanValue()) {
      compilerModuleExtension.inheritCompilerOutputPath(true);
    }

    compilerModuleExtension.setExcludeOutput(root.getChild(IdeaXml.EXCLUDE_OUTPUT_TAG) != null);

    final List entriesElements = root.getChildren(IdeaXml.CONTENT_ENTRY_TAG);
    if (!entriesElements.isEmpty()) {
      for (Object o : entriesElements) {
        readContentEntry((Element)o, model.addContentEntry(((Element)o).getAttributeValue(IdeaXml.URL_ATTR)));
      }
    } else {
      final ContentEntry[] entries = model.getContentEntries();
      if (entries.length > 0) {
        readContentEntry(root, entries[0]);
      }
    }

    for (Object o : root.getChildren("lib")) {
      Element libElement = (Element)o;
      final String libName = libElement.getAttributeValue("name");
      final Library libraryByName = model.getModuleLibraryTable().getLibraryByName(libName);
      if (libraryByName != null) {
        final LibraryOrderEntry libraryOrderEntry = model.findLibraryOrderEntry(libraryByName);
        if (libraryOrderEntry != null) {
          final String scopeAttribute = libElement.getAttributeValue("scope");
          libraryOrderEntry.setScope(scopeAttribute == null ? DependencyScope.COMPILE : DependencyScope.valueOf(scopeAttribute));
        }
        final Library.ModifiableModel modifiableModel = libraryByName.getModifiableModel();
        for (Object r : libElement.getChildren("srcroot")) {
          modifiableModel.addRoot(((Element)r).getAttributeValue("url"), OrderRootType.SOURCES);
        }
        replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, OrderRootType.SOURCES, "relative-module-src");
        replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, OrderRootType.CLASSES, "relative-module-cls");
        modifiableModel.commit();
      } else { //try to replace everywhere
        final Library[] libraries = model.getModuleLibraryTable().getLibraries();
        for (Library library : libraries) {
          final Library.ModifiableModel modifiableModel = library.getModifiableModel();
          replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, OrderRootType.SOURCES, "relative-module-src");
          replaceModuleRelatedRoots(model.getProject(), modifiableModel, libElement, OrderRootType.CLASSES, "relative-module-cls");
          modifiableModel.commit();
        }
      }
    }
  }

  private static void replaceModuleRelatedRoots(final Project project, final Library.ModifiableModel modifiableModel, final Element libElement,
                                                final OrderRootType orderRootType, final String relativeModuleName) {
    final List<String> urls = new ArrayList<String>(Arrays.asList(modifiableModel.getUrls(orderRootType)));
    for (Object r : libElement.getChildren(relativeModuleName)) {
      final String root = PathMacroManager.getInstance(project).expandPath(((Element)r).getAttributeValue("project-related"));
      for (Iterator<String> iterator = urls.iterator(); iterator.hasNext();) {
        String url = iterator.next();
        if (root.contains(VfsUtil.urlToPath(url))) {
          iterator.remove();
          modifiableModel.removeRoot(url, orderRootType);
          modifiableModel.addRoot(root, orderRootType);
          break;
        }
      }
    }
  }

  private static void readContentEntry(Element root, ContentEntry entry) {
    for (Object o : root.getChildren(IdeaXml.TEST_FOLDER_TAG)) {
      final String url = ((Element)o).getAttributeValue(IdeaXml.URL_ATTR);
      SourceFolder folderToBeTest = null;
      for (SourceFolder folder : entry.getSourceFolders()) {
        if (Comparing.strEqual(folder.getUrl(), url)) {
          folderToBeTest = folder;
          break;
        }
      }
      if (folderToBeTest != null) {
        entry.removeSourceFolder(folderToBeTest);
      }
      entry.addSourceFolder(url, true);
    }

    final String url = entry.getUrl();
    for (Object o : root.getChildren(IdeaXml.EXCLUDE_FOLDER_TAG)) {
      final String excludeUrl = ((Element)o).getAttributeValue(IdeaXml.URL_ATTR);
      try {
        if (FileUtil.isAncestor(new File(url), new File(excludeUrl), false)) { //check if it is excluded manually
          entry.addExcludeFolder(excludeUrl);
        }
      }
      catch (IOException e) {
        //ignore
      }
    }
  }
}
