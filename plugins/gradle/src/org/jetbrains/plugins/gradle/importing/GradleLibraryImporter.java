package org.jetbrains.plugins.gradle.importing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.PlatformFacade;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.jetbrains.plugins.gradle.util.GradleLog;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 2/15/12 11:32 AM
 */
public class GradleLibraryImporter {

  @NotNull private final PlatformFacade               myPlatformFacade;

  public GradleLibraryImporter(@NotNull PlatformFacade platformFacade) {
    myPlatformFacade = platformFacade;
  }

  public void importLibrary(@NotNull final GradleLibrary library, @NotNull final Project project) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        doImportLibrary(library, project);
      }
    });
  }

  private void doImportLibrary(@NotNull final GradleLibrary gradleLibrary,@NotNull Project project) {
    // Is assumed to be called from the EDT.
    final LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(project);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final LibraryTable.ModifiableModel projectLibraryModel = libraryTable.getModifiableModel();
        final Library intellijLibrary;
        try {
          intellijLibrary = projectLibraryModel.createLibrary(gradleLibrary.getName());
        }
        finally {
          projectLibraryModel.commit();
        }
        final Library.ModifiableModel libraryModel = intellijLibrary.getModifiableModel();
        try {
          registerPaths(gradleLibrary, libraryModel);
        }
        finally {
          libraryModel.commit();
        }
      }
    });
  }

  private static void registerPaths(@NotNull GradleLibrary gradleLibrary, @NotNull Library.ModifiableModel model) {
    for (LibraryPathType pathType : LibraryPathType.values()) {
      for (String path : gradleLibrary.getPaths(pathType)) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(new File(path));
        if (virtualFile == null) {
          GradleLog.LOG.warn(String.format("Can't find %s of the library '%s' at path '%s'", pathType, gradleLibrary.getName(), path));
          continue;
        }
        if (virtualFile.isDirectory()) {
          model.addRoot(virtualFile, Lazy.LIBRARY_ROOT_MAPPINGS.get(pathType));
        }
        else {
          VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
          if (jarRoot == null) {
            GradleLog.LOG.warn(String.format(
              "Can't parse contents of the jar file at path '%s' for the library '%s''", path, gradleLibrary.getName()
            ));
            continue;
          }
          model.addRoot(jarRoot, Lazy.LIBRARY_ROOT_MAPPINGS.get(pathType));
        }
      }
    }
  }

  private static class Lazy {
    private static final Map<LibraryPathType, OrderRootType> LIBRARY_ROOT_MAPPINGS
      = new EnumMap<LibraryPathType, OrderRootType>(LibraryPathType.class);

    static {
      LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.BINARY, OrderRootType.CLASSES);
      LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.SOURCE, OrderRootType.SOURCES);
      LIBRARY_ROOT_MAPPINGS.put(LibraryPathType.DOC, JavadocOrderRootType.getInstance());
      assert LibraryPathType.values().length == LIBRARY_ROOT_MAPPINGS.size();
    }
  }
}
