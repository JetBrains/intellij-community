package com.intellij.lang.ant.psi.introspection;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AntTypeDefinition {

  AntTypeDefinition[] EMPTY_ARRAY = new AntTypeDefinition[0];

  AntTypeId getTypeId();

  void setTypeId(final AntTypeId id);

  String getClassName();

  boolean isTask();
  
  boolean isAllTaskContainer();

  boolean isProperty();
  
  @NotNull
  String[] getAttributes();

  @Nullable
  AntAttributeType getAttributeType(final String attr);

  AntTypeId[] getNestedElements();

  @Nullable
  String getNestedClassName(final AntTypeId id);

  boolean isExtensionPointType(ClassLoader aClass, final String className);
  
  void registerNestedType(final AntTypeId typeId, final String className);

  void unregisterNestedType(final AntTypeId typeId);

  PsiElement getDefiningElement();

  boolean isOutdated();

  void setOutdated(boolean isOutdated);
}
