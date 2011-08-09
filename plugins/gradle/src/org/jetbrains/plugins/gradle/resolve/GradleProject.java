package org.jetbrains.plugins.gradle.resolve;

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
public interface GradleProject {

  @NotNull
  String getJdkName();
  
  @NotNull
  LanguageLevel getLanguageLevel();
  
  @NotNull
  Set<? extends GradleModule> getModules();
}
