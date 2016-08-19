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

import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.BasePathMacroManager;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.hash.HashSet;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.*;
import org.jetbrains.idea.eclipse.config.EclipseModuleManagerImpl;
import org.jetbrains.idea.eclipse.importWizard.EclipseNatureImporter;
import org.jetbrains.idea.eclipse.util.ErrorLog;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class EclipseClasspathReader extends AbstractEclipseClasspathReader<ModifiableRootModel> {
  private final Project myProject;
  private ContentEntry myContentEntry;

  public EclipseClasspathReader(@NotNull String rootPath, @NotNull Project project, @Nullable List<String> currentRoots) {
    this(rootPath, project, currentRoots, null);
  }

  public EclipseClasspathReader(@NotNull String rootPath, @NotNull Project project, @Nullable List<String> currentRoots, @Nullable Set<String> moduleNames) {
    super(rootPath, currentRoots, moduleNames);

    myProject = project;
  }

  public void init(@NotNull ModifiableRootModel model) {
    myContentEntry = model.addContentEntry(pathToUrl(myRootPath));
  }

  public static void collectVariables(Set<String> usedVariables, Element classpathElement, final String rootPath) {
    for (Element element : classpathElement.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
      String path = element.getAttributeValue(EclipseXml.PATH_ATTR);
      if (path == null) {
        continue;
      }

      String kind = element.getAttributeValue(EclipseXml.KIND_ATTR);
      if (Comparing.strEqual(kind, EclipseXml.VAR_KIND)) {
        createEPathVariable(path, 0);
        String srcPath = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
        if (srcPath != null) {
          createEPathVariable(srcPath, srcVarStart(srcPath));
        }
      }
      else if (Comparing.strEqual(kind, EclipseXml.SRC_KIND) || Comparing.strEqual(kind, EclipseXml.OUTPUT_KIND)) {
        EclipseProjectFinder.LinkedResource linkedResource = EclipseProjectFinder.findLinkedResource(rootPath, path);
        if (linkedResource != null && linkedResource.containsPathVariable()) {
          usedVariables.add(linkedResource.getVariableName());
        }
      }
    }
  }

  public void readClasspath(@NotNull ModifiableRootModel model, @NotNull Element classpathElement) throws IOException, ConversionException {
    Set<String> sink = new THashSet<>();
    readClasspath(model, sink, sink, sink, null, classpathElement);
  }

  public void readClasspath(@NotNull ModifiableRootModel model,
                            @NotNull Collection<String> unknownLibraries,
                            @NotNull Collection<String> unknownJdks,
                            Set<String> refsToModules,
                            final String testPattern,
                            Element classpathElement) throws IOException, ConversionException {
    for (OrderEntry orderEntry : model.getOrderEntries()) {
      if (!(orderEntry instanceof ModuleSourceOrderEntry)) {
        model.removeOrderEntry(orderEntry);
      }
    }
    int idx = 0;
    EclipseModuleManagerImpl eclipseModuleManager = EclipseModuleManagerImpl.getInstance(model.getModule());
    Set<String> libs = new HashSet<>();
    for (Element o : classpathElement.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
      try {
        readClasspathEntry(model, unknownLibraries, unknownJdks, refsToModules, testPattern, o, idx++,
                           eclipseModuleManager,
                           ((BasePathMacroManager)PathMacroManager.getInstance(model.getModule())).getExpandMacroMap(), libs);
      }
      catch (ConversionException e) {
        ErrorLog.rethrow(ErrorLog.Level.Warning, null, EclipseXml.CLASSPATH_FILE, e);
      }
    }
    if (!model.isSdkInherited() && model.getSdkName() == null) {
      eclipseModuleManager.setForceConfigureJDK();
      model.inheritSdk();
    }
  }

  @Override
  protected int rearrange(ModifiableRootModel rootModel) {
    return rearrangeOrderEntryOfType(rootModel, ModuleSourceOrderEntry.class);
  }

  @Override
  protected String expandEclipsePath2Url(ModifiableRootModel rootModel, String path) {
    final VirtualFile contentRoot = myContentEntry.getFile();
    if (contentRoot != null) {
      return EPathUtil.expandEclipsePath2Url(path, rootModel, myCurrentRoots, contentRoot);
    }
    return EPathUtil.expandEclipsePath2Url(path, rootModel, myCurrentRoots);
  }

  @Override
  protected Set<String> getDefinedCons() {
    return EclipseNatureImporter.getAllDefinedCons();
  }

  @Override
  protected void addModuleLibrary(ModifiableRootModel rootModel,
                                  Element element,
                                  boolean exported,
                                  String libName,
                                  String url,
                                  String srcUrl, 
                                  String nativeRoot, 
                                  ExpandMacroToPathMap macroMap) {
    final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(libName);
    final Library.ModifiableModel modifiableModel = library.getModifiableModel();
    modifiableModel.addRoot(url, OrderRootType.CLASSES);
    if (srcUrl != null) {
      modifiableModel.addRoot(srcUrl, OrderRootType.SOURCES);
    }
    
    if (nativeRoot != null) {
      modifiableModel.addRoot(nativeRoot, NativeLibraryOrderRootType.getInstance());
    }
    
    EJavadocUtil.appendJavadocRoots(element, rootModel, myCurrentRoots, modifiableModel);
    modifiableModel.commit();

    setLibraryEntryExported(rootModel, exported, library);
  }

  @Override
  protected void addJUnitDefaultLib(ModifiableRootModel rootModel, String junitName, ExpandMacroToPathMap macroMap) {
    final Library library = rootModel.getModuleLibraryTable().getModifiableModel().createLibrary(junitName);
    final Library.ModifiableModel modifiableModel = library.getModifiableModel();
    modifiableModel.addRoot(getJunitClsUrl(junitName.contains("4")), OrderRootType.CLASSES);
    modifiableModel.commit();
  }

  @Override
  protected void addSourceFolderToCurrentContentRoot(ModifiableRootModel rootModel,
                                                     String srcUrl,
                                                     boolean testFolder) {
    myContentEntry.addSourceFolder(srcUrl, testFolder);
  }

  @Override
  protected void addSourceFolder(ModifiableRootModel rootModel, String srcUrl, boolean testFolder) {
    rootModel.addContentEntry(srcUrl).addSourceFolder(srcUrl, testFolder);
  }

  @Override
  protected void setUpModuleJdk(ModifiableRootModel rootModel,
                                Collection<String> unknownJdks,
                                EclipseModuleManager eclipseModuleManager,
                                String jdkName) {
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
        eclipseModuleManager.setInvalidJdk(jdkName);
        unknownJdks.add(jdkName);
      }
    }
    rearrangeOrderEntryOfType(rootModel, JdkOrderEntry.class);
  }

  @Override
  protected void addInvalidModuleEntry(ModifiableRootModel rootModel, boolean exported, String moduleName) {
    rootModel.addInvalidModuleEntry(moduleName).setExported(exported);
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

  @Override
  public void setupOutput(ModifiableRootModel rootModel, final String path) {
    setOutputUrl(rootModel, path);
  }

  public static void setOutputUrl(@NotNull ModifiableRootModel rootModel, @NotNull String path) {
    CompilerModuleExtension compilerModuleExtension = rootModel.getModuleExtension(CompilerModuleExtension.class);
    compilerModuleExtension.setCompilerOutputPath(pathToUrl(path));
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

  @Override
  protected void addNamedLibrary(final ModifiableRootModel rootModel,
                                 final Collection<String> unknownLibraries,
                                 final boolean exported,
                                 final String name,
                                 final boolean applicationLevel) {
    Library lib = findLibraryByName(myProject, name);
    if (lib != null) {
      rootModel.addLibraryEntry(lib).setExported(exported);
    }
    else {
      unknownLibraries.add(name);
      rootModel.addInvalidLibrary(name, applicationLevel ? LibraryTablesRegistrar.APPLICATION_LEVEL : LibraryTablesRegistrar.PROJECT_LEVEL).setExported(exported);
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

  static String getJunitClsUrl(final boolean version4) {
    String url = version4 ? JavaSdkUtil.getJunit4JarPath() : JavaSdkUtil.getJunit3JarPath();
    final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(pathToUrl(url));
    if (localFile != null) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
      url = jarFile != null ? jarFile.getUrl() : localFile.getUrl();
    }

    return url;
  }


  protected String prepareValidUrlInsideJar(String url) {
    final VirtualFile localFile = VirtualFileManager.getInstance().findFileByUrl(url);
    if (localFile != null) {
      final VirtualFile jarFile = JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
      if (jarFile != null) {
        return jarFile.getUrl();
      }
    }

    return url;
  }
}
