// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.types.CodeReferenceKind
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement

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

private val scriptBodyDeclarationsKey = Key.create<CachedValue<Array<GrVariableDeclaration>>>("groovy.script.declarations.body")
private val scriptDeclarationsKey = Key.create<CachedValue<Array<GrVariableDeclaration>>>("groovy.script.declarations.all")

private fun GroovyFileImpl.collectScriptDeclarations(topLevelOnly: Boolean): Array<GrVariableDeclaration> {
  val key = if (topLevelOnly) scriptBodyDeclarationsKey else scriptDeclarationsKey
  val provider = {
    Result.create(doCollectScriptDeclarations(topLevelOnly), this)
  }
  return CachedValuesManager.getManager(project).getCachedValue(this, key, provider, false)
}

private fun GroovyFileImpl.doCollectScriptDeclarations(topLevelOnly: Boolean): Array<GrVariableDeclaration> {
  val result = mutableListOf<GrVariableDeclaration>()
  accept(object : PsiRecursiveElementWalkingVisitor() {
    override fun visitElement(element: PsiElement?) {
      if (element is GrVariableDeclaration && element.modifierList.rawAnnotations.isNotEmpty()) {
        result.add(element)
      }
      if (element is GrTypeDefinition) return //  do not go into classes
      if (element is GrMethod && topLevelOnly) return // do not go into methods if top level only
      super.visitElement(element)
    }
  })
  return result.toTypedArray()
}

fun GrCodeReferenceElement.doGetKind(): CodeReferenceKind {
  val parent = parent
  return when (parent) {
    is GrPackageDefinition -> CodeReferenceKind.PACKAGE_REFERENCE
    is GrImportStatement -> CodeReferenceKind.IMPORT_REFERENCE
    is GrCodeReferenceElement -> parent.kind
    else -> CodeReferenceKind.REFERENCE
  }
}

fun getQualifiedReferenceName(reference: GrReferenceElement<*>): String? {
  val parts = mutableListOf<String>()
  var current = reference
  while (true) {
    val name = current.referenceName ?: return null
    parts.add(name)
    val qualifier = current.qualifier ?: break
    qualifier as? GrReferenceElement<*> ?: return null
    current = qualifier
  }
  return parts.reversed().joinToString(separator = ".")
}
