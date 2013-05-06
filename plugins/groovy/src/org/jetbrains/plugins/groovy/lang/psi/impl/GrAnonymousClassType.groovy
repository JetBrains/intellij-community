/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement
/**
 * @author Max Medvedev
 */
public class GrAnonymousClassType extends GrLiteralClassType {
  @NotNull private final GrAnonymousClassDefinition myAnonymous;

  public GrAnonymousClassType(@NotNull LanguageLevel languageLevel,
                              @NotNull GlobalSearchScope scope,
                              @NotNull JavaPsiFacade facade,
                              @NotNull GrAnonymousClassDefinition anonymous) {
    super(languageLevel, scope, facade);
    myAnonymous = anonymous;
  }

  @NotNull
  @Override
  protected String getJavaClassName() {
    final GrCodeReferenceElement ref = myAnonymous.baseClassReferenceGroovy;
    final PsiElement resolved = ref.resolve();
    if (resolved instanceof PsiClass) {
      return resolved.qualifiedName;
    }
    else {
      return ref.classNameText
    }
  }

  @NotNull
  @Override
  public String getClassName() {
    StringUtil.getShortName(javaClassName);
  }

  @Override
  GrAnonymousClassDefinition resolve() {
    myAnonymous
  }

  @NotNull
  @Override
  public PsiType[] getParameters() {
    myAnonymous.baseClassReferenceGroovy.typeArguments
  }

  @NotNull
  @Override
  public PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    new GrAnonymousClassType(languageLevel, myScope, myFacade, myAnonymous)
  }

  @Override
  public String getInternalCanonicalText() { canonicalText }

  @Override
  public boolean isValid() { myAnonymous.valid }

  @Override
  String toString() { "AnonymousType:$presentableText" }

  @NotNull
  PsiClassType getSimpleClassType() {
    new GrClassReferenceType(myAnonymous.baseClassReferenceGroovy, myLanguageLevel)
  }
}
