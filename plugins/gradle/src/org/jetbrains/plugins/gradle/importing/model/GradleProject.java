package org.jetbrains.plugins.gradle.importing.model;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Defines IntelliJ project view to the application configured via gradle.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/11 12:05 PM
 */
public interface GradleProject extends Named, GradleEntity {

  @NotNull
  String getCompileOutputPath();
  
  @NotNull
  String getJdkName();
  
  @NotNull
  LanguageLevel getLanguageLevel();
  void setLanguageLevel(@NotNull LanguageLevel level);
  
  @NotNull
  Set<? extends GradleModule> getModules();
  
  @NotNull
  Set<? extends GradleLibrary> getLibraries();
  /**
   * Offers given library to register for the current project.
   * 
   * @param library  library to register
   * @return         <code>true</code> if no such a library is already registered (given library is stored);
   *                 <code>false</code> if such a library (in terms of {@link Object#equals(Object)}) is already registered
   *                 within the current project (it's not replaced by the given one then)
   */
  boolean addLibrary(@NotNull GradleLibrary library);
}
