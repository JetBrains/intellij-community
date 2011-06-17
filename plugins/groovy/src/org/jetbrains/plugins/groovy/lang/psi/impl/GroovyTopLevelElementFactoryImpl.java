/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Medvedev Max
 */
public class GroovyTopLevelElementFactoryImpl implements PsiTopLevelElementFactory {
  private final GroovyPsiElementFactory factory;

  public GroovyTopLevelElementFactoryImpl(Project project) {
    factory = GroovyPsiElementFactory.getInstance(project);
  }

  @NotNull
  @Override
  public PsiClass createClass(@NonNls @NotNull String name) throws IncorrectOperationException {
    return factory.createTypeDefinition("class " + name + "{}");
  }

  @NotNull
  @Override
  public PsiClass createInterface(@NonNls @NotNull String name) throws IncorrectOperationException {
    return factory.createTypeDefinition("interface " + name + "{}");
  }

  @NotNull
  @Override
  public PsiClass createEnum(@NotNull @NonNls String name) throws IncorrectOperationException {
    return factory.createTypeDefinition("enum " + name + "{}");
  }

  @NotNull
  @Override
  public PsiField createField(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException {
    final GrVariableDeclaration fieldDeclaration = factory.createFieldDeclaration(new String[]{PsiModifier.PRIVATE}, name, null, type);
    return (PsiField)fieldDeclaration.getVariables()[0];
  }

  @NotNull
  @Override
  public PsiMethod createMethod(@NotNull @NonNls String name, PsiType returnType) throws IncorrectOperationException {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    builder.append("public");
    if (returnType != null) {
      builder.append(' ');
      builder.append(returnType.getCanonicalText());
    }
    builder.append(' ').append(name).append("(){}");
    return factory.createMethodFromText(builder.toString());
  }

  @NotNull
  @Override
  public PsiMethod createConstructor() {
    return factory.createConstructorFromText("Foo", "", null);
  }

  @NotNull
  @Override
  public PsiClassInitializer createClassInitializer() throws IncorrectOperationException {
    final GrTypeDefinition typeDefinition = factory.createTypeDefinition("class X {{}}");
    return typeDefinition.getInitializers()[0];
  }

  @NotNull
  @Override
  public PsiParameter createParameter(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @NotNull
  @Override
  public PsiParameterList createParameterList(@NotNull @NonNls String[] names, @NotNull PsiType[] types) throws IncorrectOperationException {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }
}
