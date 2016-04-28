/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduceParameterObject;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectClassDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrParameterInfo;

public class GroovyIntroduceObjectClassDescriptor extends IntroduceParameterObjectClassDescriptor<GrMethod, GrParameterInfo> {
  public GroovyIntroduceObjectClassDescriptor(String className,
                                              String packageName,
                                              boolean useExistingClass,
                                              boolean createInnerClass,
                                              String newVisibility,
                                              boolean generateAccessors,
                                              GrParameterInfo[] parameters) {
    super(className, packageName, useExistingClass, createInnerClass, newVisibility, generateAccessors, parameters);
  }

  @Override
  public String getSetterName(GrParameterInfo paramInfo, @NotNull PsiElement context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getGetterName(GrParameterInfo paramInfo, @NotNull PsiElement context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public GrMethod findCompatibleConstructorInExistingClass(GrMethod method) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement createClass(GrMethod method, ReadWriteAccessDetector.Access[] accessors) {
    throw new UnsupportedOperationException();
  }
}
