package com.intellij.lang.ant.psi;

import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import org.jetbrains.annotations.Nullable;

public interface AntTypeDef extends AntTask {

  @Nullable
  String getDefinedName();

  @Nullable
  String getClassName();

  @Nullable
  String getClassPath();

  @Nullable
  String getClassPathRef();

  @Nullable
  String getLoaderRef();

  @Nullable
  String getFormat();

  @Nullable
  String getUri();

  AntTypeDefinition getDefinition();
}
