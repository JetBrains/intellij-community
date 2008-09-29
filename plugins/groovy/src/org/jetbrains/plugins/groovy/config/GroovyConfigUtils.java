/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.config;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.grails.config.GrailsConfigUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author ilyas
 */
public class GroovyConfigUtils {
  private static final String GROOVY_STARTER_FILE_NAME = "groovy";
  public static final String UNDEFINED_VERSION = "undefined";
  public static final String GROOVY_LIB_PATTERN = "groovy-.*";
  public static final String GROOVY_JAR_PATTERN = "groovy-all-.*\\.jar";
  public static final String GROOVY_LIB_PREFIX = "groovy-";

  public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
  public static final String DGM_CLASS_PATH = "org/codehaus/groovy/runtime/DefaultGroovyMethods.class";
  public static final String CLOSURE_CLASS_PATH = "org/codehaus/groovy/runtime/DefaultGroovyMethods.class";
  private static final Condition<Library> GROOVY_LIB_CONDITION = new Condition<Library>() {
    public boolean value(Library library) {
      return isGroovySdkLibrary(library);
    }
  };

  public static boolean isGroovySdkHome(final VirtualFile file) {
    final Ref<Boolean> result = Ref.create(false);
    processFilesUnderGDKRoot(file, new Processor<VirtualFile>() {
      public boolean process(final VirtualFile virtualFile) {
        result.set(true);
        return false;
      }
    });
    return result.get().booleanValue();
  }

  private static void processFilesUnderGDKRoot(VirtualFile file, final Processor<VirtualFile> processor) {
    if (file != null && file.isDirectory()) {
      final VirtualFile child = file.findChild("bin");

      if (child != null && child.isDirectory()) {
        for (VirtualFile grandChild : child.getChildren()) {
          if (GROOVY_STARTER_FILE_NAME.equals(grandChild.getNameWithoutExtension())) {
            if (!processor.process(grandChild)) return;
          }
        }
      }
    }
  }

  @NotNull
  public static String getGroovyVersion(@NotNull String path) {
    String groovyJarVersion = getGroovyGrailsJarVersion(path + "/lib", "groovy-\\d.*\\.jar", MANIFEST_PATH);
    return groovyJarVersion != null ? groovyJarVersion : UNDEFINED_VERSION;
  }

  /**
   * Return value of Implementation-Version attribute in jar manifest
   * <p/>
   *
   * @param jarPath      directory containing jar file
   * @param jarRegex     filename pattern for jar file
   * @param manifestPath path to manifest file in jar file
   * @return value of Implementation-Version attribute, null if not found
   */
  public static String getGroovyGrailsJarVersion(String jarPath, final String jarRegex, String manifestPath) {
    try {
      File[] jars = GroovyUtils.getFilesInDirectoryByPattern(jarPath, jarRegex);
      if (jars.length != 1) {
        return null;
      }
      JarFile jarFile = new JarFile(jars[0]);
      JarEntry jarEntry = jarFile.getJarEntry(manifestPath);
      if (jarEntry == null) {
        return null;
      }
      Manifest manifest = new Manifest(jarFile.getInputStream(jarEntry));
      return manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
    }
    catch (Exception e) {
      return null;
    }
  }

  public static Library[] getProjectGroovyLibraries(Project project) {
    if (project == null) return new Library[0];
    final LibraryTable table = ProjectLibraryTable.getInstance(project);
    final List<Library> all = ContainerUtil.findAll(table.getLibraries(), GROOVY_LIB_CONDITION);
    return all.toArray(new Library[all.size()]);
  }

  public static Library[] getAllGroovyLibraries(@Nullable Project project) {
    return ArrayUtil.mergeArrays(getGlobalGroovyLibraries(), getProjectGroovyLibraries(project), Library.class);
  }

  public static Library[] getGlobalGroovyLibraries() {
    return LibrariesUtil.getGlobalLibraries(GROOVY_LIB_CONDITION);
  }

  public static String[] getGroovyLibNames() {
    return LibrariesUtil.getLibNames(getGlobalGroovyLibraries());
  }

  public static boolean isGroovySdkLibrary(Library library) {
    if (library == null) return false;
    VirtualFile[] classFiles = library.getFiles(OrderRootType.CLASSES);
    for (VirtualFile file : classFiles) {
      String path = file.getPath();
      if (path != null && "jar".equals(file.getExtension())) {
        path = StringUtil.trimEnd(path, "!/");
        @NonNls String name = file.getName();
        if (isGroovyAllJar(name) || name.matches(GROOVY_LIB_PATTERN)) {
          File realFile = new File(path);
          if (realFile.exists()) {
            try {
              JarFile jarFile = new JarFile(realFile);
              return isGroovyJar(jarFile) && !GrailsConfigUtils.isGrailsSdkLibrary(library);
            }
            catch (IOException e) {
              return false;
            }
          }
        }
      }
    }
    return false;
  }

  public static boolean isGroovyAllJar(@NonNls final String name) {
    return name.matches(GROOVY_JAR_PATTERN) || name.equals("groovy-all.jar");
  }

  public static boolean isGroovyJar(JarFile jarFile) {
    return jarFile.getJarEntry(MANIFEST_PATH) != null &&
           jarFile.getJarEntry(CLOSURE_CLASS_PATH) != null &&
           jarFile.getJarEntry(DGM_CLASS_PATH) != null;
  }

  @NotNull
  public static String getGroovyLibVersion(Library library) {
    return getGroovyVersion(LibrariesUtil.getGroovyOrGrailsLibraryHome(library));
  }

  public static GroovySDK[] getGroovySDKs(final Module module) {
    final GroovySDK[] projectSdks =
      ContainerUtil.map2Array(getProjectGroovyLibraries(module.getProject()), GroovySDK.class, new Function<Library, GroovySDK>() {
        public GroovySDK fun(final Library library) {
          return new GroovySDK(library, module, true);
        }
      });
    final GroovySDK[] globals = ContainerUtil.map2Array(getGlobalGroovyLibraries(), GroovySDK.class, new Function<Library, GroovySDK>() {
      public GroovySDK fun(final Library library) {
        return new GroovySDK(library, module, false);
      }
    });
    return ArrayUtil.mergeArrays(globals, projectSdks, GroovySDK.class);
  }

  public static void updateGroovyLibInModule(@NotNull Module module, @Nullable GroovySDK sdk) {
    ModuleRootManager manager = ModuleRootManager.getInstance(module);
    ModifiableRootModel model = manager.getModifiableModel();
    removeGroovyLibrariesFormModule(model);
    if (sdk == null || sdk.getLibrary() == null) {
      model.commit();
      return;
    }

    saveGroovyDefaultLibName(sdk.getLibraryName());
    Library newLib = sdk.getLibrary();
    LibraryOrderEntry addedEntry = model.addLibraryEntry(newLib);
    LibrariesUtil.placeEntryToCorrectPlace(model, addedEntry);
    model.commit();
  }

  public static void removeGroovyLibrariesFormModule(ModifiableRootModel model) {
    OrderEntry[] entries = model.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libEntry = (LibraryOrderEntry)entry;
        Library library = libEntry.getLibrary();
        if (isGroovySdkLibrary(library)) {
          model.removeOrderEntry(entry);
        }
      }
    }
  }

  public static Library[] getGroovyLibrariesByModule(final Module module) {
    final Condition<Library> condition = new Condition<Library>() {
      public boolean value(Library library) {
        return isGroovySdkLibrary(library);
      }
    };
    return LibrariesUtil.getLibrariesByCondition(module, condition);
  }

  public static ValidationResult isGroovySdkHome(String path) {
    if (path != null) {
      final VirtualFile relativeFile = VfsUtil.findRelativeFile(path, null);
      if (relativeFile != null && isGroovySdkHome(relativeFile)) {
        return ValidationResult.OK;
      }
    }
    return new ValidationResult(GroovyBundle.message("invalid.groovy.sdk.path.message"));
  }

  @Nullable
  public static Library createGroovyLibrary(final String path,
                                            final String name,
                                            final Project project,
                                            final boolean inModuleSettings,
                                            final boolean inProject) {
    if (project == null) return null;
    final Ref<Library> libRef = new Ref<Library>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Library library = createGroovyLibImmediately(path, name, project, inModuleSettings, inProject);
        libRef.set(library);
      }
    });
    return libRef.get();
  }

  private static Library createGroovyLibImmediately(String path,
                                                    String name,
                                                    Project project,
                                                    boolean inModuleSettings,
                                                    final boolean inProject) {
    String version = getGroovyVersion(path);
    String libName = name != null ? name : generateNewGroovyLibName(version, project);
    if (path.length() > 0) {
      // create library
      LibraryTable.ModifiableModel modifiableModel = null;
      Library library;

      if (inModuleSettings) {
        StructureConfigurableContext context = ModuleStructureConfigurable.getInstance(project).getContext();
        LibraryTableModifiableModelProvider provider = context
          .createModifiableModelProvider(inProject ? LibraryTablesRegistrar.PROJECT_LEVEL : LibraryTablesRegistrar.APPLICATION_LEVEL, true);
        modifiableModel = provider.getModifiableModel();
        library = modifiableModel.createLibrary(libName);
      } else {
        LibraryTable libTable =
          inProject ? ProjectLibraryTable.getInstance(project) : LibraryTablesRegistrar.getInstance().getLibraryTable();
        library = libTable.getLibraryByName(libName);
        if (library == null) {
          library = LibraryUtil.createLibrary(libTable, libName);
        }
      }

      // fill library
      final Library.ModifiableModel model;
      if (inModuleSettings) {
        model = ((LibrariesModifiableModel)modifiableModel).getLibraryEditor(library).getModel();
      } else {
        model = library.getModifiableModel();
      }
      File srcRoot = new File(path + "/src/main");
      if (srcRoot.exists()) {
        model.addRoot(VfsUtil.getUrlForLibraryRoot(srcRoot), OrderRootType.SOURCES);
      }

      File[] jars;
      File embeddableDir = new File(path + "/embeddable");
      if (embeddableDir.exists()) {
        jars = embeddableDir.listFiles();
      } else {
        jars = new File(path + "/lib").listFiles();
      }
      if (jars != null) {
        for (File file : jars) {
          if (file.getName().endsWith(".jar")) {
            model.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
          }
        }
      }
      if (!inModuleSettings) {
        model.commit();
      }
      return library;
    }
    return null;
  }

  public static String generateNewGroovyLibName(String version, final Project project) {
    String prefix = GROOVY_LIB_PREFIX;
    return LibrariesUtil.generateNewLibraryName(version, prefix, project);
  }

  public static void saveGroovyDefaultLibName(String name) {
    GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();
    if (!UNDEFINED_VERSION.equals(name)) {
      settings.DEFAULT_GROOVY_LIB_NAME = name;
    }
  }

  @Nullable
  public static String getGroovyDefaultLibName() {
    GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();
    return settings.DEFAULT_GROOVY_LIB_NAME;
  }

  public static Library createLibFirstTime(String baseName) {
    LibraryTable libTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    Library library = libTable.getLibraryByName(baseName);
    if (library == null) {
      library = LibraryUtil.createLibrary(libTable, baseName);
    }
    return library;
  }

  public static void removeOldRoots(Library.ModifiableModel model) {
    for (OrderRootType type : OrderRootType.getAllTypes()) {
      for (String url : model.getUrls(type)) {
        model.removeRoot(url, type);
      }
    }
  }

  public static Collection<String> getGroovyVersions(final Module module) {
    return ContainerUtil.map2List(getAllGroovyLibraries(module.getProject()), new Function<Library, String>() {
      public String fun(Library library) {
        return getGroovyLibVersion(library);
      }
    });
  }

  public static boolean isGroovyConfigured(Module module) {
    return module != null &&
           FacetManager.getInstance(module).getFacetByType(GroovyFacet.ID) != null ||
           GrailsConfigUtils.isGrailsConfigured(module);
  }

  @NotNull
  public static String getGroovyInstallPath(Module module) {
    if (module == null) return "";
    Library[] libraries = getGroovyLibrariesByModule(module);
    if (libraries.length == 0) return "";
    Library library = libraries[0];
    return LibrariesUtil.getGroovyOrGrailsLibraryHome(library);
  }

  public static void setUpGroovyFacet(final ModifiableRootModel model) {
    LibraryTable libTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    final Project project = model.getModule().getProject();
    String name = GroovyApplicationSettings.getInstance().DEFAULT_GROOVY_LIB_NAME;
    if (name != null && libTable.getLibraryByName(name) != null) {
      Library library = libTable.getLibraryByName(name);
      if (isGroovySdkLibrary(library)) {
        LibraryOrderEntry entry = model.addLibraryEntry(library);
        LibrariesUtil.placeEntryToCorrectPlace(model, entry);
      }
    } else {
      final Library[] libraries = getAllGroovyLibraries(project);
      if (libraries.length > 0) {
        Library library = libraries[0];
        if (isGroovySdkLibrary(library)) {
          LibraryOrderEntry entry = model.addLibraryEntry(library);
          LibrariesUtil.placeEntryToCorrectPlace(model, entry);
        }
      }
    }
  }

  public static boolean tryToSetUpGroovyFacetOntheFly(final Module module) {
    final Project project = module.getProject();
    final Library[] libraries = getAllGroovyLibraries(project);
    if (libraries.length > 0) {
      final Library library = libraries[0];
      int result = Messages.showOkCancelDialog(
        GroovyBundle.message("groovy.like.library.found.text", library.getName(), getGroovyLibVersion(library)),
        GroovyBundle.message("groovy.like.library.found"), GroovyIcons.GROOVY_ICON_32x32);
      final Ref<Boolean> ref = new Ref<Boolean>();
      ref.set(false);
      if (result == 0) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
            LibraryOrderEntry entry = model.addLibraryEntry(libraries[0]);
            LibrariesUtil.placeEntryToCorrectPlace(model, entry);
            model.commit();
            ref.set(true);
          }
        });
      }
      return ref.get();
    }
    return false;
  }
}
