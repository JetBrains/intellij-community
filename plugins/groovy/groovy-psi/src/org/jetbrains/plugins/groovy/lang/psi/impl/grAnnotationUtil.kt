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
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList

fun PsiAnnotation.findDeclaredDetachedValue(attributeName: String?): PsiAnnotationMemberValue? {
  return AnnotationUtil.findDeclaredAttribute(this, attributeName)?.detachedValue
}

fun PsiAnnotationMemberValue?.booleanValue() = (this as? PsiLiteral)?.value as? Boolean

fun PsiAnnotationMemberValue?.stringValue() = (this as? PsiLiteral)?.value as? String

fun GrModifierList.hasAnnotation(fqn: String) = annotations.any { it.qualifiedName == fqn }
