package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Defines IntelliJ module view to the application configured via gradle.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 12:10 PM
 */
public interface GradleModule {
  
  @NotNull
  String getName();
  
  @NotNull
  Collection<? extends GradleContentRoot> getContentRoots();

  boolean isInheritProjectCompileOutputPath();

  /**
   * Allows to get file system path of the compile output of the source of the target type.
   * 
   * @param type  target source type
   * @return      file system path to use for compile output for the target source type;
   *              {@link GradleProject#getCompileOutputPath() project compile output path} should be used if current module
   *              doesn't provide specific compile output path
   */
  @Nullable
  String getCompileOutputPath(@NotNull SourceType type);

  @NotNull
  Collection<GradleDependency> getDependencies();
}
