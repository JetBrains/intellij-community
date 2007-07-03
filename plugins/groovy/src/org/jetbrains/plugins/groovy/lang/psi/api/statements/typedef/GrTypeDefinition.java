/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTopLevelDefintion;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public interface GrTypeDefinition extends GrNamedElement, GrTopStatement, NavigationItem, PsiClass, GrTopLevelDefintion {
  String DEFAULT_BASE_CLASS_NAME = "groovy.lang.GroovyObjectSupport";

  public GrTypeDefinition[] EMPTY_ARRAY = new GrTypeDefinition[0];

  public GrTypeDefinitionBody getBody();

  public GrStatement[] getStatements();

  @Nullable
  public String getQualifiedName();

  GrWildcardTypeArgument[] getTypeParametersGroovy();

  @NotNull
  PsiElement getNameIdentifierGroovy();

  @NotNull
  GrField[] getFields();
}
