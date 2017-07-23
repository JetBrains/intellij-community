/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyPolyVariantReference;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;

/**
 * @author ven
 */
public interface GrReferenceElement<Q extends PsiElement> extends GroovyPsiElement, GroovyPolyVariantReference, GrQualifiedReference<Q> {

  @Override
  @Nullable
  String getReferenceName();

  @Nullable
  @Override
  default PsiElement resolve() {
    return advancedResolve().getElement();
  }

  @NotNull
  PsiType[] getTypeArguments();

  @Nullable
  GrTypeArgumentList getTypeArgumentList();

  @NotNull
  String getClassNameText();
}
