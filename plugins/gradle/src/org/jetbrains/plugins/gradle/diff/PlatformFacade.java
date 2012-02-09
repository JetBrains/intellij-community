package org.jetbrains.plugins.gradle.diff;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

/**
 * IntelliJ code provides a lot of statical bindings to the interested pieces of data. For example we need to execute code
 * like below to get list of modules for the target project:
 * <pre>
 *   ModuleManager.getInstance(project).getModules()
 * </pre>
 * That means that it's not possible to test target classes in isolation if corresponding infrastructure is not set up.
 * However, we don't want to set it up if we execute a simple standalone test.
 * <p/>
 * This interface is intended to encapsulate access to the underlying IntelliJ functionality.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/26/12 11:32 AM
 */
public interface PlatformFacade {

  @NotNull
  LibraryTable getProjectLibraryTable(@NotNull Project project);
  
  @NotNull
  LanguageLevel getLanguageLevel(@NotNull Project project);

  @NotNull
  Collection<Module> getModules(@NotNull Project project);

  @NotNull
  Collection<OrderEntry> getOrderEntries(@NotNull Module module);

  /**
   * @return    icon that should be used for representation project root node at the tree UI controls used by the gradle integration
   */
  @NotNull
  Icon getProjectIcon();

  /**
   * Allows to derive from the given VFS file path that may be compared to the path used by the gradle api.
   * <p/>
   * Generally, this method is necessary for processing binary library paths - they point to jar files and VFS uses
   * <code>'!'</code> marks in their paths internally.
   * 
   * @param file  target file
   * @return      given file's path that may be compared to the one used by the gradle api
   */
  @NotNull
  String getLocalFileSystemPath(@NotNull VirtualFile file);
}
