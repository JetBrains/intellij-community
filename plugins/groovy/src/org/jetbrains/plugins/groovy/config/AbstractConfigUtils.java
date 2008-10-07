package org.jetbrains.plugins.groovy.config;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.util.LibrarySDK;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * @author ilyas
 */
public abstract class AbstractConfigUtils {

  // SDK-dependent entities
  @NonNls protected String STARTER_SCRIPT_FILE_NAME;
  protected String[] KEY_CLASSES;
  protected String ERR_MESSAGE;
  @NonNls protected String SDK_LIB_PREFIX;

  private Condition<Library> LIB_SEARCH_CONDITION = new Condition<Library>() {
    public boolean value(Library library) {
      return isSDKLibrary(library);
    }
  };

  // Common entities
  @NonNls public static final String UNDEFINED_VERSION = "undefined";
  @NonNls public static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";


  /**
   * Define, whether  given home is appropriate SDK home
   *
   * @param file
   * @return
   */
  public boolean isSDKHome(final VirtualFile file) {
    final Ref<Boolean> result = Ref.create(false);
    processFilesUnderSDKRoot(file, new Processor<VirtualFile>() {
      public boolean process(final VirtualFile virtualFile) {
        result.set(true);
        return false;
      }
    });
    return result.get().booleanValue();
  }

  private void processFilesUnderSDKRoot(VirtualFile file, final Processor<VirtualFile> processor) {
    if (file != null && file.isDirectory()) {
      final VirtualFile child = file.findChild("bin");
      if (child != null && child.isDirectory()) {
        for (VirtualFile grandChild : child.getChildren()) {
          if (STARTER_SCRIPT_FILE_NAME.equals(grandChild.getNameWithoutExtension())) {
            if (!processor.process(grandChild)) return;
          }
        }
      }
    }
  }

  @NotNull
  public abstract String getSDKVersion(@NotNull String path);

  public String getSDKLibPrefix() {
    return SDK_LIB_PREFIX;
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
  @Nullable
  public String getSDKJarVersion(String jarPath, final String jarRegex, String manifestPath) {
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

  public Library[] getProjectSDKLibraries(Project project) {
    if (project == null) return new Library[0];
    final LibraryTable table = ProjectLibraryTable.getInstance(project);
    final List<Library> all = ContainerUtil.findAll(table.getLibraries(), LIB_SEARCH_CONDITION);
    return all.toArray(new Library[all.size()]);
  }

  public Library[] getAllSDKLibraries(@Nullable Project project) {
    return ArrayUtil.mergeArrays(getGlobalSDKLibraries(), getProjectSDKLibraries(project), Library.class);
  }

  public Library[] getGlobalSDKLibraries() {
    return LibrariesUtil.getGlobalLibraries(LIB_SEARCH_CONDITION);
  }

  public String[] getSDKLibNames() {
    return LibrariesUtil.getLibNames(getGlobalSDKLibraries());
  }

  public abstract boolean isSDKLibrary(Library library);

  public boolean isSDKJar(JarFile jarFile) {
    if (jarFile.getJarEntry(MANIFEST_PATH) == null) return false;
    for (String s : KEY_CLASSES) {
      if (jarFile.getJarEntry(s) == null) return false;
    }
    return true;
  }

  @NotNull
  public String getSDKLibVersion(Library library) {
    return getSDKVersion(LibrariesUtil.getGroovyOrGrailsLibraryHome(library));
  }

  public abstract LibrarySDK[] getSDKs(final Module module);

  public void updateSDKLibInModule(@NotNull Module module, @Nullable LibrarySDK sdk) {
    ModuleRootManager manager = ModuleRootManager.getInstance(module);
    ModifiableRootModel model = manager.getModifiableModel();
    removeSDKLibrariesFromModule(model);
    if (sdk == null || sdk.getLibrary() == null) {
      model.commit();
      return;
    }

    saveSDKDefaultLibName(sdk.getLibraryName());
    Library newLib = sdk.getLibrary();
    LibraryOrderEntry addedEntry = model.addLibraryEntry(newLib);
    LibrariesUtil.placeEntryToCorrectPlace(model, addedEntry);
    model.commit();
  }

  public void removeSDKLibrariesFromModule(ModifiableRootModel model) {
    OrderEntry[] entries = model.getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        LibraryOrderEntry libEntry = (LibraryOrderEntry)entry;
        Library library = libEntry.getLibrary();
        if (isSDKLibrary(library)) {
          model.removeOrderEntry(entry);
        }
      }
    }
  }

  public Library[] getSDKLibrariesByModule(final Module module) {
    final Condition<Library> condition = new Condition<Library>() {
      public boolean value(Library library) {
        return isSDKLibrary(library);
      }
    };
    return LibrariesUtil.getLibrariesByCondition(module, condition);
  }

  public ValidationResult isSDKHome(String path) {
    if (path != null) {
      final VirtualFile relativeFile = VfsUtil.findRelativeFile(path, null);
      if (relativeFile != null && isSDKHome(relativeFile)) {
        return ValidationResult.OK;
      }
    }
    return new ValidationResult(ERR_MESSAGE);
  }

  @Nullable
  public Library createSDKLibrary(final String path,
                                  final String name,
                                  final Project project,
                                  final boolean inModuleSettings,
                                  final boolean inProject) {
    final Ref<Library> libRef = new Ref<Library>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        Library library = createSDKLibImmediately(path, name, project, inModuleSettings, inProject);
        libRef.set(library);
      }
    });
    return libRef.get();
  }

  protected abstract Library createSDKLibImmediately(String path,
                                                     String name,
                                                     Project project,
                                                     boolean inModuleSettings,
                                                     final boolean inProject);

  public String generateNewSDKLibName(String version, final Project project) {
    String prefix = SDK_LIB_PREFIX;
    return LibrariesUtil.generateNewLibraryName(version, prefix, project);
  }

  public abstract void saveSDKDefaultLibName(String name);

  @Nullable
  public abstract String getSDKDefaultLibName();

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

  public Collection<String> getSDKVersions(final Project project) {
    return ContainerUtil.map2List(getAllSDKLibraries(project), new Function<Library, String>() {
      public String fun(Library library) {
        return getSDKLibVersion(library);
      }
    });
  }

  public abstract boolean isSDKConfigured(Module module);

  @NotNull
  public abstract String getSDKInstallPath(Module module);

}
