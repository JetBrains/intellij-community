package com.intellij.lang.ant.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntProject extends AntStructuredElement {

  @Nullable
  String getBaseDir();

  @Nullable
  String getDescription();

  @NotNull
  AntTarget[] getTargets();

  @Nullable
  AntTarget getTarget(final String name);

  @Nullable
  AntTarget getDefaultTarget();

  @NotNull
  AntTarget[] getImportedTargets();

  @NotNull
  AntFile[] getImportedFiles();

  void registerRefId(final String id, AntElement element);

  @Nullable
  AntElement getElementByRefId(String refid);

  @NotNull
  String[] getRefIds();

  void addEnvironmentPropertyPrefix(@NotNull final String envPrefix);

  boolean isEnvironmentProperty(@NotNull final String propName);
}
