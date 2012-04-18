package org.jetbrains.plugins.gradle.diff.library;

import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleAbstractConflictingPropertyChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 2/2/12 1:32 PM
 */
public class GradleMismatchedLibraryPathChange extends GradleAbstractConflictingPropertyChange<Set<String>> {
  
  public GradleMismatchedLibraryPathChange(@NotNull Library entity,
                                           @NotNull Set<String> gradleValue,
                                           @NotNull Set<String> intellijValue)
    throws IllegalArgumentException
  {
    super(GradleEntityIdMapper.mapEntityToId(entity),
          GradleBundle.message("gradle.sync.change.library.path", GradleUtil.getLibraryName(entity)),
          gradleValue,
          intellijValue);
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this); 
  }
}
