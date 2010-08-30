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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.jetbrains.idea.eclipse.conversion.EPathUtil.*;

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
        createEPathVariable(usedVariables, path, 0);
        final String srcPath = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
        if (srcPath != null) {
          createEPathVariable(usedVariables, srcPath, srcVarStart(srcPath));
        }
      } else if (Comparing.strEqual(kind, EclipseXml.SRC_KIND)) {
        if (EclipseProjectFinder.isExternalResource(rootPath, path)) {
          usedVariables.add(EclipseProjectFinder.extractPathVariableName(path));
        }
      }
    }
  }

  private static int srcVarStart(String srcPath) {
    return srcPath.startsWith("/") ? 1 : 0;
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
    int idx = 0;
    for (Object o : classpathElement.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
      try {
        readClasspathEntry(model, unknownLibraries, unknownJdks, usedVariables, refsToModules, testPattern, (Element)o, idx++);
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
                                  Element element, int idx) throws ConversionException {
    String kind = element.getAttributeValue(EclipseXml.KIND_ATTR);
    if (kind == null) {
      throw new ConversionException("Missing classpathentry/@kind");
    }


    String path = element.getAttributeValue(EclipseXml.PATH_ATTR);
    if (path == null) {
      throw new ConversionException("Missing classpathentry/@path");
    }

    final boolean exported = EclipseXml.TRUE_VALUE.equals(element.getAttributeValue(EclipseXml.EXPORTED_ATTR));

    final EclipseModuleManager eclipseModuleManager = EclipseModuleManager.getInstance(rootModel.getModule());
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
          eclipseModuleManager.registerEclipseLinkedSrcVarPath(srcUrl, path);

          rootModel.addContentEntry(srcUrl).addSourceFolder(srcUrl, isTestFolder);
        } else {
          getContentEntry().addSourceFolder(srcUrl, isTestFolder);
        }
        eclipseModuleManager.setExpectedModuleSourcePlace(rearrangeOrderEntryOfType(rootModel, ModuleSourceOrderEntry.class));
        eclipseModuleManager.registerSrcPlace(srcUrl, idx);
      }
    }

    else if (kind.equals(EclipseXml.OUTPUT_KIND)) {
      setupOutput(rootModel, myRootPath + "/" + path);
    }

    else if (kind.equals(EclipseXml.LIB_KIND)) {
      final String libName = getPresentableName(path);
      final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(libName);
      final Library.ModifiableModel modifiableModel = library.getModifiableModel();

      final String url = expandEclipsePath2Url(path, rootModel, myCurrentRoots);
      modifiableModel.addRoot(url, OrderRootType.CLASSES);
      eclipseModuleManager.registerEclipseLibUrl(url);

      final String sourcePath = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
      if (sourcePath != null) {
        final String srcUrl = expandEclipsePath2Url(sourcePath, rootModel, myCurrentRoots);
        modifiableModel.addRoot(srcUrl, OrderRootType.SOURCES);
      }

      EJavadocUtil.appendJavadocRoots(element, rootModel, myCurrentRoots, modifiableModel);
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

      final String url = eclipseVariabledPath2Url(rootModel, usedVariables, path, 0);
      modifiableModel.addRoot(url, OrderRootType.CLASSES);
      eclipseModuleManager.registerEclipseVariablePath(url, path);

      final String srcPathAttr = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
      if (srcPathAttr != null) {
        final String srcUrl = eclipseVariabledPath2Url(rootModel, usedVariables, srcPathAttr, srcVarStart(srcPathAttr));
        modifiableModel.addRoot(srcUrl, OrderRootType.SOURCES);
        eclipseModuleManager.registerEclipseSrcVariablePath(srcUrl, srcPathAttr);
      }

      EJavadocUtil.appendJavadocRoots(element, rootModel, myCurrentRoots, modifiableModel);

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
        eclipseModuleManager.registerUnknownCons(path);
        addNamedLibrary(rootModel, new ArrayList<String>(), exported, path, LibraryTablesRegistrar.APPLICATION_LEVEL);
      }
    }
    else {
      throw new ConversionException("Unknown classpathentry/@kind: " + kind);
    }
  }

  private static int rearrangeOrderEntryOfType(ModifiableRootModel rootModel, Class<? extends OrderEntry> orderEntryClass) {
    OrderEntry[] orderEntries = rootModel.getOrderEntries();
    int moduleSourcesIdx = 0;
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntryClass.isAssignableFrom(orderEntry.getClass())) {
        break;
      }
      moduleSourcesIdx++;
    }
    orderEntries = ArrayUtil.append(orderEntries, orderEntries[moduleSourcesIdx]);
    orderEntries = ArrayUtil.remove(orderEntries, moduleSourcesIdx);
    rootModel.rearrangeOrderEntries(orderEntries);
    return orderEntries.length - 1;
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

  static String getJunitClsUrl(final boolean version4) {
    String url = version4 ? JavaSdkUtil.getJunit4JarPath() : JavaSdkUtil.getJunit3JarPath();
    final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.pathToUrl(url));
    if (localFile != null) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
      url = jarFile != null ? jarFile.getUrl() : localFile.getUrl();
    }
    return url;
  }


  private static String eclipseVariabledPath2Url(ModifiableRootModel rootModel, Set<String> usedVariables, String path, int varStart) {
    final EPathVariable var = createEPathVariable(usedVariables, path, varStart);
    final String url = PathMacroManager.getInstance(rootModel.getModule()).expandPath(var.toIdeaVariabledUrl());

    final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(url);
    if (localFile != null) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
      if (jarFile != null) {
        return jarFile.getUrl();
      }
    }

    return url;
  }

  private static EPathVariable createEPathVariable(final Set<String> usedVariables, final String pathAttr, final int varStart) {
    final EPathVariable var;
    int slash = pathAttr.indexOf("/", varStart);
    if (slash > 0) {
      var = new EPathVariable(usedVariables, pathAttr.substring(varStart, slash), pathAttr.substring(slash + 1));
    }
    else {
      var = new EPathVariable(usedVariables, pathAttr.substring(varStart), null);
    }
    return var;
  }

  private static class EPathVariable {
    private final String myVariable;
    private final String myRelatedPath;

    private EPathVariable(final Set<String> usedVariables, final String variable, final String relatedPath) {
      myVariable = variable;
      myRelatedPath = relatedPath;
      usedVariables.add(myVariable);
    }

    public String toIdeaVariabledUrl() {
      return VfsUtil.pathToUrl(getVariableRelatedPath(myVariable, myRelatedPath));
    }
  }
}
