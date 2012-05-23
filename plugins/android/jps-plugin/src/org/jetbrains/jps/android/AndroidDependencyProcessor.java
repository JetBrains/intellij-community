package org.jetbrains.jps.android;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class AndroidDependencyProcessor {
  public void processExternalLibrary(@NotNull File file) {
  }

  public void processAndroidLibraryPackage(@NotNull File file) {
  }

  public void processAndroidLibraryOutputDirectory(@NotNull File dir) {
  }

  public void processJavaModuleOutputDirectory(@NotNull File dir) {
  }

  public abstract boolean isToProcess(@NotNull AndroidDependencyType type);
}
