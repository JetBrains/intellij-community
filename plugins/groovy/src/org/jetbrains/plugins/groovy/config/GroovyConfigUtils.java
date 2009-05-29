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

import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.grails.config.GrailsConfigUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.util.LibrarySDK;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;

/**
 * @author ilyas
 */
public abstract class GroovyConfigUtils extends AbstractConfigUtils {
  @NonNls private static final String GROOVY_ALL_JAR_PATTERN = "groovy-all-(.*)\\.jar";

  @NonNls private static final String DGM_CLASS_PATH = "org/codehaus/groovy/runtime/DefaultGroovyMethods.class";
  @NonNls private static final String CLOSURE_CLASS_PATH = "groovy/lang/Closure.class";

  private static GroovyConfigUtils myGroovyConfigUtils;
  @NonNls private static final String GROOVY_JAR_PATTERN = "groovy-(\\d.*)\\.jar";

  private GroovyConfigUtils() {
  }

  public static GroovyConfigUtils getInstance() {
    if (myGroovyConfigUtils == null) {
      myGroovyConfigUtils = new GroovyConfigUtils() {
        {
          SDK_LIB_PREFIX = "groovy-";
          KEY_CLASSES = new String[]{DGM_CLASS_PATH, CLOSURE_CLASS_PATH};
          ERR_MESSAGE = GroovyBundle.message("invalid.groovy.sdk.path.message");
          STARTER_SCRIPT_FILE_NAME = "groovy";
        }};
    }
    return myGroovyConfigUtils;
  }

  @NotNull
  public String getSDKVersion(@NotNull final String path) {
    String groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_JAR_PATTERN, MANIFEST_PATH);
    if (groovyJarVersion != null) {
      return groovyJarVersion;
    }

    groovyJarVersion = getSDKJarVersion(path + "/lib", GROOVY_ALL_JAR_PATTERN, MANIFEST_PATH);
    if (groovyJarVersion != null) {
      return groovyJarVersion;
    }
    
    return UNDEFINED_VERSION;
  }

  public boolean isSDKLibrary(Library library) {
    if (library == null) return false;
    return isGroovyLibrary(library.getFiles(OrderRootType.CLASSES));
  }

  public static boolean isGroovyLibrary(VirtualFile[] classFiles) {
    for (VirtualFile file : classFiles) {
      if (isGroovyAllJar(file.getName())) {
        return true;
      }
    }
    return false;
  }

  public String getSDKVersion(@NotNull Library library) {
    return getSDKVersion(LibrariesUtil.getGroovyOrGrailsLibraryHome(library));
  }

  public static boolean isGroovyAllJar(@NonNls final String name) {
    return name.matches(GROOVY_ALL_JAR_PATTERN) || name.matches(GROOVY_JAR_PATTERN);
  }

  public LibrarySDK[] getSDKs(final Module module) {
    final GroovySDK[] projectSdks =
      ContainerUtil.map2Array(getProjectSDKLibraries(module.getProject()), GroovySDK.class, new Function<Library, GroovySDK>() {
        public GroovySDK fun(final Library library) {
          return new GroovySDK(library, module, true);
        }
      });
    final GroovySDK[] globals = ContainerUtil.map2Array(getGlobalSDKLibraries(), GroovySDK.class, new Function<Library, GroovySDK>() {
      public GroovySDK fun(final Library library) {
        return new GroovySDK(library, module, false);
      }
    });
    return ArrayUtil.mergeArrays(globals, projectSdks, GroovySDK.class);
  }

  protected Library createSDKLibImmediately(String path, String name, Project project, boolean inModuleSettings, final boolean inProject) {
    String version = getSDKVersion(path);
    String libName = name != null ? name : generateNewSDKLibName(version, project);
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
      File libDir = new File(path + "/lib");
      if (libDir.exists()) {
        jars = libDir.listFiles();
      } else {
        jars = new File(path + "/embeddable").listFiles();
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
      if (modifiableModel != null) {
        modifiableModel.commit();
      }
      return library;
    }
    return null;
  }

  public void saveSDKDefaultLibName(String name) {
    GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();
    if (!UNDEFINED_VERSION.equals(name)) {
      settings.DEFAULT_GROOVY_LIB_NAME = name;
    }
  }

  @Nullable
  public String getSDKDefaultLibName() {
    GroovyApplicationSettings settings = GroovyApplicationSettings.getInstance();
    return settings.DEFAULT_GROOVY_LIB_NAME;
  }

  public boolean isSDKConfigured(Module module) {
    return module != null && FacetManager.getInstance(module).getFacetByType(GroovyFacet.ID) != null ||
           GrailsConfigUtils.getInstance().isSDKConfigured(module);
  }

  @NotNull
  public String getSDKInstallPath(Module module) {
    if (module == null) return "";
    Library[] libraries = getSDKLibrariesByModule(module);
    if (libraries.length == 0) return "";
    Library library = libraries[0];
    return LibrariesUtil.getGroovyOrGrailsLibraryHome(library);
  }

  public void setUpGroovyFacet(final ModifiableRootModel model) {
    LibraryTable libTable = LibraryTablesRegistrar.getInstance().getLibraryTable();
    final Project project = model.getModule().getProject();
    String name = GroovyApplicationSettings.getInstance().DEFAULT_GROOVY_LIB_NAME;
    if (name != null && libTable.getLibraryByName(name) != null) {
      Library library = libTable.getLibraryByName(name);
      if (isSDKLibrary(library)) {
        LibraryOrderEntry entry = model.addLibraryEntry(library);
        LibrariesUtil.placeEntryToCorrectPlace(model, entry);
      }
    } else {
      final Library[] libraries = getAllSDKLibraries(project);
      if (libraries.length > 0) {
        Library library = libraries[0];
        if (isSDKLibrary(library)) {
          LibraryOrderEntry entry = model.addLibraryEntry(library);
          LibrariesUtil.placeEntryToCorrectPlace(model, entry);
        }
      }
    }
  }

  @Override
  public boolean isSDKHome(VirtualFile file) {
    if (file != null && file.isDirectory()) {
      final String path = file.getPath();
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/lib", GROOVY_JAR_PATTERN).length > 0) {
        return true;
      }
      if (GroovyUtils.getFilesInDirectoryByPattern(path + "/embeddable", GROOVY_ALL_JAR_PATTERN).length > 0) {
        return true;
      }
    }
    return false;
  }

  public boolean tryToSetUpGroovyFacetOntheFly(final Module module) {
    final Project project = module.getProject();
    final Library[] libraries = getAllSDKLibraries(project);
    if (libraries.length > 0) {
      final Library library = libraries[0];
      int result = Messages
        .showOkCancelDialog(GroovyBundle.message("groovy.like.library.found.text", module.getName(), library.getName(), getSDKLibVersion(library)),
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
            final FacetManager manager = FacetManager.getInstance(module);
            final GroovyFacetType type = GroovyFacetType.getInstance();
            final FacetTypeId<GroovyFacet> id = type.getId();
            final GroovyFacet facetByType = manager.getFacetByType(id);
            if (facetByType == null) {
              manager.addFacet(type, type.getPresentableName(), null);
            }
          }
        });
      }
      return ref.get().booleanValue();
    }
    return false;
  }
}
