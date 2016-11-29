/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.dsl.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author ilyas
 */
public class PsiElementCategory implements PsiEnhancerCategory {

  @Nullable
  public static PsiElement bind(PsiElement element) {
    PsiElement elem = element instanceof GrMethodCall ? ((GrMethodCall)element).getInvokedExpression() : element;
    final PsiReference ref = elem.getReference();
    return ref == null ? null : ref.resolve();
  }

  @Nullable
  public static PsiElement getQualifier(PsiElement elem){
    if (elem instanceof GrReferenceExpression) {
      return ((GrReferenceExpression)elem).getQualifierExpression();
    }
    return null;
  }

  @NotNull
  public static Collection<? extends PsiElement> asList(@Nullable PsiElement elem) {
    if (elem == null) return new ArrayList<>();
    if (elem instanceof GrListOrMap) {
      return Arrays.asList(((GrListOrMap)elem).getInitializers());
    } else if (elem instanceof GrAnnotationArrayInitializer){
      return Arrays.asList(((GrAnnotationArrayInitializer)elem).getInitializers());
    } else {
      return Collections.singleton(elem);
    }
  }

  public static Object eval(PsiElement elem) {
    if (elem instanceof GrLiteral) {
      GrLiteral literal = (GrLiteral)elem;
      return literal.getValue();
    }
    return elem;
  }

}
