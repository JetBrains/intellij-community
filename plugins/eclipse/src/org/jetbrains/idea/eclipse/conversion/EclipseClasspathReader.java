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
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.EclipseXml;
import org.jetbrains.idea.eclipse.IdeaXml;
import org.jetbrains.idea.eclipse.config.EclipseModuleManager;
import org.jetbrains.idea.eclipse.importWizard.EclipseProjectFinder;
import org.jetbrains.idea.eclipse.util.ErrorLog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.jetbrains.idea.eclipse.conversion.ERelativePathUtil.*;

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
  }

  public static void collectVariables(Set<String> usedVariables, Element classpathElement, final String rootPath) {
    for (Object o : classpathElement.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
      final Element element = (Element)o;
      String path = element.getAttributeValue(EclipseXml.PATH_ATTR);
      if (path == null) continue;
      final String kind = element.getAttributeValue(EclipseXml.KIND_ATTR);
      if (Comparing.strEqual(kind, EclipseXml.VAR_KIND)) {
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
      } else if (Comparing.strEqual(kind, EclipseXml.SRC_KIND)) {
        if (EclipseProjectFinder.isExternalResource(rootPath, path)) {
          usedVariables.add(EclipseProjectFinder.extractPathVariableName(path));
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
    if (!model.isSdkInherited() && model.getSdkName() == null) {
      EclipseModuleManager.getInstance(model.getModule()).setForceConfigureJDK();
      model.inheritSdk();
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
        String srcUrl = VfsUtil.pathToUrl(myRootPath + "/" + path);
        final boolean isTestFolder = testPattern != null && testPattern.length() > 0 && path.matches(testPattern);
        if (EclipseProjectFinder.isExternalResource(myRootPath, path)) {
          final String varName = EclipseProjectFinder.extractPathVariableName(path);
          usedVariables.add(varName);

          final String toPathVariableFormat =
            getVariableRelatedPath(varName, path.length() > varName.length() ? path.substring(varName.length()) : null);
          srcUrl = VfsUtil.pathToUrl(PathMacroManager.getInstance(rootModel.getModule()).expandPath(toPathVariableFormat));
          EclipseModuleManager.getInstance(rootModel.getModule()).registerEclipseLinkedSrcVarPath(srcUrl, path);

          rootModel.addContentEntry(srcUrl).addSourceFolder(srcUrl, isTestFolder);
        } else {
          getContentEntry().addSourceFolder(srcUrl, isTestFolder);
        }
        rearrangeOrderEntryOfType(rootModel, ModuleSourceOrderEntry.class);
      }
    }

    else if (kind.equals(EclipseXml.OUTPUT_KIND)) {
      setupOutput(rootModel, myRootPath + "/" + path);
    }

    else if (kind.equals(EclipseXml.LIB_KIND)) {
      final String libName = getPresentableName(path);
      final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(libName);
      final Library.ModifiableModel modifiableModel = library.getModifiableModel();

      modifiableModel.addRoot(getUrl(path, rootModel), OrderRootType.CLASSES);

      final String sourcePath = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
      if (sourcePath != null) {
        modifiableModel.addRoot(getUrl(sourcePath, rootModel), OrderRootType.SOURCES);
      }

      final List<String> docPaths = EJavadocUtil.getJavadocAttribute(element, rootModel, myCurrentRoots);
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

      final String url = getUrl(PathMacroManager.getInstance(rootModel.getModule()).expandPath(getVariableRelatedPath(clsVar, clsPath)),
                                rootModel);
      EclipseModuleManager.getInstance(rootModel.getModule()).registerEclipseVariablePath(url, path);
      modifiableModel.addRoot(url, OrderRootType.CLASSES);

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
        final String srcUrl = getUrl(PathMacroManager.getInstance(rootModel.getModule()).expandPath(getVariableRelatedPath(srcVar, srcPath)),
                                     rootModel);
        EclipseModuleManager.getInstance(rootModel.getModule()).registerEclipseSrcVariablePath(srcUrl, srcPathAttr);
        modifiableModel.addRoot(srcUrl, OrderRootType.SOURCES);
      }

      final List<String> docPaths = EJavadocUtil.getJavadocAttribute(element, rootModel, myCurrentRoots);
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
        rearrangeOrderEntryOfType(rootModel, JdkOrderEntry.class);
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
      } else {
        EclipseModuleManager.getInstance(rootModel.getModule()).registerUnknownCons(path);
        addNamedLibrary(rootModel, new ArrayList<String>(), exported, path, LibraryTablesRegistrar.APPLICATION_LEVEL);
      }
    }
    else {
      throw new ConversionException("Unknown classpathentry/@kind: " + kind);
    }
  }

  private static void rearrangeOrderEntryOfType(ModifiableRootModel rootModel, Class<? extends OrderEntry> orderEntryClass) {
    OrderEntry[] orderEntries = rootModel.getOrderEntries();
    int moduleSourcesIdx = 0;
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntryClass.isAssignableFrom(orderEntry.getClass())) {
        break;
      }
      moduleSourcesIdx++;
    }
    orderEntries = ArrayUtil.append(orderEntries, orderEntries[moduleSourcesIdx]);
    rootModel.rearrangeOrderEntries(ArrayUtil.remove(orderEntries, moduleSourcesIdx));
  }

  public static void setupOutput(ModifiableRootModel rootModel, final String path) {
    final CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
    compilerModuleExtension.setCompilerOutputPath(VfsUtil.pathToUrl(path));
    compilerModuleExtension.inheritCompilerOutputPath(false);
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
    Library lib = findLibraryByName(myProject, name);
    if (lib != null) {
      rootModel.addLibraryEntry(lib).setExported(exported);
    }
    else {
      unknownLibraries.add(name);
      rootModel.addInvalidLibrary(name, notFoundLibraryLevel).setExported(exported);
    }
  }

  public static Library findLibraryByName(Project project, String name) {
    final LibraryTablesRegistrar tablesRegistrar = LibraryTablesRegistrar.getInstance();
    Library lib = tablesRegistrar.getLibraryTable().getLibraryByName(name);
    if (lib == null) {
      lib = tablesRegistrar.getLibraryTable(project).getLibraryByName(name);
    }
    if (lib == null) {
      for (LibraryTable table : tablesRegistrar.getCustomLibraryTables()) {
        lib = table.getLibraryByName(name);
        if (lib != null) {
          break;
        }
      }
    }
    return lib;
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

  private String getUrl(final String path, ModifiableRootModel model) {
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
        if (otherModule != null && otherModule != model.getModule()) {
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

  static String getJunitClsUrl(final boolean version4) {
    String url = version4 ? JavaSdkUtil.getJunit4JarPath() : JavaSdkUtil.getJunit3JarPath();
    final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.pathToUrl(url));
    if (localFile != null) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
      url = jarFile != null ? jarFile.getUrl() : localFile.getUrl();
    }
    return url;
  }

}
