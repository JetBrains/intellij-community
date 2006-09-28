package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTypeDef extends AntTask {

  @Nullable
  String getDefinedName();

  @Nullable
  String getClassName();

  @Nullable
  String getClassPath();

  @Nullable
  String getUri();

  @Nullable
  String getFile();

  @Nullable
  String getResource();

  @NonNls
  @Nullable
  String getFormat();

  @NotNull
  AntTypeDefinition[] getDefinitions();

  boolean typesLoaded();

  @Nullable
  String getLocalizedError();
}
