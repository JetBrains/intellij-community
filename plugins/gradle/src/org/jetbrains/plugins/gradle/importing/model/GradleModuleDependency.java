package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:32 PM
 */
public interface GradleModuleDependency extends GradleDependency {

  Comparator<GradleModuleDependency> COMPARATOR = new Comparator<GradleModuleDependency>() {
    @Override
    public int compare(GradleModuleDependency o1, GradleModuleDependency o2) {
      return Named.COMPARATOR.compare(o1.getModule(), o2.getModule());
    }
  };
  
  @NotNull
  GradleModule getModule();
}
