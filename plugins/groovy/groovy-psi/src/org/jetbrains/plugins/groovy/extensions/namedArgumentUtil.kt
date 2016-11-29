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
package org.jetbrains.plugins.groovy.extensions

import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel

fun getDescriptors(literal: GrListOrMap): Map<String, NamedArgumentDescriptor> = CachedValuesManager.getCachedValue(literal) {
  val map = mutableMapOf<String, NamedArgumentDescriptor>()

  for (ext in GroovyNamedArgumentProvider.EP_NAME.extensions) {
    map += ext.getNamedArguments(literal)
  }

  Result.create(map, PsiModificationTracker.MODIFICATION_COUNT)
}

fun getDescriptor(label: GrArgumentLabel?): NamedArgumentDescriptor? {
  val name = (label ?: return null).name ?: return null
  val literal = PsiTreeUtil.getParentOfType(label, GrListOrMap::class.java) ?: return null
  return getDescriptors(literal)[name]
}

fun getReferenceFromDescriptor(label: GrArgumentLabel?): PsiPolyVariantReference? {
  return label?.let { getDescriptor(it)?.createReference(it) }
}