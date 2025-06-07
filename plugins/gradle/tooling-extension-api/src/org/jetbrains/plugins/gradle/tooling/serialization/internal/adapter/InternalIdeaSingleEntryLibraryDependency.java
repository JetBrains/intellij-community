// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter;

import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

@ApiStatus.Internal
public final class InternalIdeaSingleEntryLibraryDependency extends InternalIdeaDependency implements IdeaSingleEntryLibraryDependency {
  private final @NotNull File myFile;
  private @Nullable File mySource;
  private @Nullable File myJavadoc;
  private InternalGradleModuleVersion myModuleVersion;

  public InternalIdeaSingleEntryLibraryDependency(@NotNull File file) {myFile = file;}

  @Override
  public @NotNull File getFile() {
    return myFile;
  }

  @Override
  public @Nullable File getSource() {
    return mySource;
  }

  public void setSource(@Nullable File source) {
    mySource = source;
  }

  @Override
  public @Nullable File getJavadoc() {
    return myJavadoc;
  }

  @Override
  public boolean isExported() {
    return getExported();
  }

  public void setJavadoc(@Nullable File javadoc) {
    myJavadoc = javadoc;
  }

  @Override
  public GradleModuleVersion getGradleModuleVersion() {
    return myModuleVersion;
  }

  public void setModuleVersion(InternalGradleModuleVersion moduleVersion) {
    myModuleVersion = moduleVersion;
  }

  @Override
  public String toString() {
    return "IdeaSingleEntryLibraryDependency{" +
           "myFile=" + myFile +
           ", mySource=" + mySource +
           ", myJavadoc=" + myJavadoc +
           ", myModuleVersion=" + myModuleVersion +
           "} " + super.toString();
  }
}
