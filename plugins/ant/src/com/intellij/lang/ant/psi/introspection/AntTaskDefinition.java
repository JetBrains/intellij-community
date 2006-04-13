package com.intellij.lang.ant.psi.introspection;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

public interface AntTaskDefinition extends Cloneable {

  Pair<String, String> getTaskId();

  String getClassName();

  String[] getAttributes();

  AntAttributeType getAttributeType(String attr);

  Pair<String, String>[] getNestedElements();

  @Nullable
  String getNestedClassName(Pair<String, String> taskId);
}
