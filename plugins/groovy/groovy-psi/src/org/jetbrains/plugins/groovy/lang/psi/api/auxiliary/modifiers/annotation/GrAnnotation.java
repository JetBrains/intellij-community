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

package org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation;

import com.intellij.psi.PsiAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public interface GrAnnotation extends GrCondition, PsiAnnotation, GrAnnotationMemberValue {

  GrAnnotation[] EMPTY_ARRAY = new GrAnnotation[0];

  @NotNull
  GrCodeReferenceElement getClassReference();

  @NotNull
  String getShortName();

  @Override
  @NotNull
  GrAnnotationArgumentList getParameterList();
}
