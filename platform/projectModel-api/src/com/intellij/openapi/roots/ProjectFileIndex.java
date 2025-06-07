// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.workspace.jps.entities.LibraryEntity;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Provides information about files contained in a project. Should be used from a read action.
 *
 * @see ProjectRootManager#getFileIndex()
 */
@ApiStatus.NonExtendable
public interface ProjectFileIndex extends FileIndex {

  /**
   * @deprecated use {@link ProjectFileIndex#getInstance(Project)} instead
   */
  @Deprecated
  final class SERVICE {
    private SERVICE() { }

    public static ProjectFileIndex getInstance(Project project) {
      return ProjectFileIndex.getInstance(project);
    }
  }

  static @NotNull ProjectFileIndex getInstance(@NotNull Project project) {
    return project.getService(ProjectFileIndex.class);
  }

  /**
   * Returns {@code true} if {@code file} is located under project content or library roots and not excluded or ignored
   */
  @RequiresReadLock
  boolean isInProject(@NotNull VirtualFile file);

  /**
   * Returns {@code true} if {@code file} is located under project content or library roots, regardless of whether it's marked as excluded or not,
   * and returns {@code false} if {@code file} is located outside or it or one of its parent directories is ignored.
   */
  @RequiresReadLock
  boolean isInProjectOrExcluded(@NotNull VirtualFile file);

  /**
   * Returns module to which content the specified file belongs or null if the file does not belong to content of any module.
   */
  @RequiresReadLock
  @Nullable
  Module getModuleForFile(@NotNull VirtualFile file);

  /**
   * Returns module to which content the specified file belongs or null if the file does not belong to content of any module.
   *
   * @param honorExclusion if {@code false} the containing module will be returned even if the file is located under a folder marked as excluded
   */
  @RequiresReadLock
  @Nullable
  Module getModuleForFile(@NotNull VirtualFile file, boolean honorExclusion);

  /**
   * Returns the list of modules which content roots contain the specified file or an empty list if the file does not belong to the content of any module.
   *
   * @param honorExclusion if {@code false} the containing module will be returned even if the file is located under a folder marked as excluded
   */
  @ApiStatus.Internal
  @RequiresReadLock
  @NotNull
  @Unmodifiable Set<Module> getModulesForFile(@NotNull VirtualFile file, boolean honorExclusion);

  /**
   * Returns the order entries which contain the specified file (either in CLASSES or SOURCES).
   */
  @RequiresReadLock
  @NotNull
  @Unmodifiable
  List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile file);

  /**
   * Returns a classpath entry to which the specified file or directory belongs.
   *
   * @return the file for the classpath entry, or null if the file is not a compiled
   *         class file or directory belonging to a library.
   */
  @RequiresReadLock
  @Nullable
  VirtualFile getClassRootForFile(@NotNull VirtualFile file);

  /**
   * Returns the module source root or library source root to which the specified file or directory belongs.
   *
   * @return the file for the source root, or null if the file is not located under any of the source roots for the module.
   */
  @RequiresReadLock
  @Nullable
  VirtualFile getSourceRootForFile(@NotNull VirtualFile file);

  /**
   * Returns the module content root to which the specified file or directory belongs or null if the file does not belong to content of any module.
   */
  @RequiresReadLock
  @Nullable
  VirtualFile getContentRootForFile(@NotNull VirtualFile file);

  /**
   * Returns the module content root to which the specified file or directory belongs or null if the file does not belong to content of any module.
   *
   * @param honorExclusion if {@code false} the containing content root will be returned even if the file is located under a folder marked as excluded
   */
  @RequiresReadLock
  @Nullable
  VirtualFile getContentRootForFile(@NotNull VirtualFile file, final boolean honorExclusion);

  /**
   * @deprecated use {@link com.intellij.openapi.roots.PackageIndex#getPackageNameByDirectory(VirtualFile)} from Java plugin instead.
   */
  @Deprecated
  @RequiresReadLock
  @Nullable
  String getPackageNameByDirectory(@NotNull VirtualFile dir);

  /**
   * Returns true if {@code file} is a file which belongs to the classes (not sources) of some library which is included into dependencies
   * of some module.
   * @deprecated name of this method may be misleading, actually it doesn't check that {@code file} has the 'class' extension. 
   * Use {@link #isInLibraryClasses} with additional {@code !file.isDirectory()} check instead.   
   */
  @Deprecated
  @RequiresReadLock
  boolean isLibraryClassFile(@NotNull VirtualFile file);

  /**
   * Returns true if {@code fileOrDir} is a file or directory from production/test source root of some module or sources of some library,
   * which is included in dependencies of some module.
   * <br>
   * Note that this method doesn't take the exact type of the containing source root into account. 
   * If you're interested if the file is located under a root of a specific type (e.g., if you want to distinguish Java source and Java 
   * resource files), use {@link #isUnderSourceRootOfType(VirtualFile, Set)} instead.
   */
  @RequiresReadLock
  boolean isInSource(@NotNull VirtualFile fileOrDir);

  /**
   * Returns true if {@code fileOrDir} belongs to classes of some library which is included into dependencies of some module.
   */
  @RequiresReadLock
  boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir);

  /**
   * @return true if the file belongs to the classes or sources of a library added to dependencies of the project,
   *         false otherwise
   */
  @RequiresReadLock
  boolean isInLibrary(@NotNull VirtualFile fileOrDir);

  /**
   * Returns true if {@code fileOrDir} is a file or directory from sources of some library which is included into dependencies
   * of some module.
   */
  @RequiresReadLock
  boolean isInLibrarySource(@NotNull VirtualFile fileOrDir);

  /**
   * Checks if the specified file or directory is located under project roots but the file itself or one of its parent directories is
   * either excluded from the project or ignored by {@link FileTypeRegistry#isFileIgnored(VirtualFile)}).
   *
   * @return true if {@code file} is excluded or ignored, false otherwise.
   */
  @RequiresReadLock
  boolean isExcluded(@NotNull VirtualFile file);

  /**
   * Returns libraries used in the project which have {@code fileOrDir} under their classes or source roots.
   * <strong>Currently this method doesn't search for global libraries.</strong>
   */
  @ApiStatus.Experimental
  @NotNull @Unmodifiable Collection<@NotNull LibraryEntity> findContainingLibraries(@NotNull VirtualFile fileOrDir);

  /**
   * Checks if the specified file or directory is located under project roots but the file itself or one of its parent directories is ignored
   * by {@link FileTypeRegistry#isFileIgnored(VirtualFile)}).
   *
   * @return true if {@code file} is ignored, false otherwise.
   */
  @RequiresReadLock
  boolean isUnderIgnored(@NotNull VirtualFile file);

  /**
   * Returns type of the module source root which contains the given {@code file}, or {@code null} if {@code file} doesn't belong to sources 
   * of modules.
   */
  @RequiresReadLock
  @Nullable JpsModuleSourceRootType<?> getContainingSourceRootType(@NotNull VirtualFile file);

  /**
   * Returns {@code true} if {@code file} is located under a source root which is marked as containing generated sources. This method is 
   * mostly for internal use only. If you need to check if a source file is generated, it's better to use {@link com.intellij.openapi.roots.GeneratedSourcesFilter#isGeneratedSourceByAnyFilter} instead.
   */
  @RequiresReadLock
  boolean isInGeneratedSources(@NotNull VirtualFile file);

  /**
   * @deprecated use other methods from this class to obtain the information you need to get from {@link SourceFolder} instance, e.g. 
   * {@link #getContainingSourceRootType} or {@link #isInGeneratedSources}.
   */
  @Deprecated(forRemoval = true)
  @RequiresReadLock
  default @Nullable SourceFolder getSourceFolder(@NotNull VirtualFile fileOrDir) {
    return null;
  }

  /**
   * Returns name of the unloaded module to which content {@code fileOrDir} belongs, or {@code null} if {@code fileOrDir} doesn't belong
   * to an unloaded module.
   */
  @Nullable String getUnloadedModuleNameForFile(@NotNull VirtualFile fileOrDir);
}
