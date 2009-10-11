/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
