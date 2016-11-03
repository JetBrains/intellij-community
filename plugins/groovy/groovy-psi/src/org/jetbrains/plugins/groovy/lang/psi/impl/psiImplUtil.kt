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
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

internal fun GroovyFileImpl.getScriptDeclarations(topLevelOnly: Boolean): Array<out GrVariableDeclaration> {
  val tree = stubTree ?: return collectScriptDeclarations(topLevelOnly)
  return if (topLevelOnly) {
    val root: StubElement<*> = tree.root
    root.getChildrenByType(GroovyElementTypes.VARIABLE_DEFINITION, GrVariableDeclaration.EMPTY_ARRAY)
  }
  else {
    tree.plainList.filter {
      it.stubType === GroovyElementTypes.VARIABLE_DEFINITION
    }.map {
      it.psi as GrVariableDeclaration
    }.toTypedArray()
  }
}

private val key = Key.create<ParameterizedCachedValue<Array<GrVariableDeclaration>, Boolean>>("groovy.variable.declarations")

private fun GroovyFileImpl.collectScriptDeclarations(topLevelOnly: Boolean): Array<GrVariableDeclaration> {
  return if (topLevelOnly) {
    CachedValuesManager.getCachedValue(this) {
      Result.create(doCollectScriptBodyDeclarations(), this)
    }
  }
  else {
    CachedValuesManager.getCachedValue(this) {
      Result.create(doCollectScriptDeclarations(), this)
    }
  }
}

private fun GroovyFile.doCollectScriptBodyDeclarations(): Array<GrVariableDeclaration> {
  val result = mutableListOf<GrVariableDeclaration>()
  accept(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement?) {
      if (element is GrVariableDeclaration) {
        if (element.modifierList.rawAnnotations.isNotEmpty()) {
          result.add(element)
        }
      }
      if (element !is GrMethod && element !is GrTypeDefinition) { // do no go into method and classes
        super.visitElement(element)
      }
    }
  })
  return result.toTypedArray()
}

private fun GroovyFileImpl.doCollectScriptDeclarations(): Array<GrVariableDeclaration> {
  val result = mutableListOf<GrVariableDeclaration>()
  accept(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement?) {
      if (element is GrVariableDeclaration) {
        if (element.modifierList.rawAnnotations.isNotEmpty()) {
          result.add(element)
        }
      }
      if (element !is GrTypeDefinition) { //  do not go into classes
        super.visitElement(element)
      }
    }
  })
  return result.toTypedArray()
}