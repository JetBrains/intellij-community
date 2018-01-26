/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.psi.uast

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.uast.*
import java.util.*

class GrUFile(override val psi: GroovyFile, override val languagePlugin: UastLanguagePlugin) : UFile, JvmDeclarationUElement {
  override val packageName: String
    get() = psi.packageName

  override val imports = emptyList<UImportStatement>() // not implemented

  override val annotations: List<UAnnotation>
    get() = psi.packageDefinition?.annotationList?.annotations?.map { GrUAnnotation(it, { this }) } ?: emptyList()

  override val classes by lazy { psi.classes.mapNotNull { (it as? GrTypeDefinition)?.let { GrUClass(it, { this }) } } }

  override val allCommentsInFile by lazy {
    val comments = ArrayList<UComment>(0)
    psi.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitComment(comment: PsiComment) {
        comments += UComment(comment, this@GrUFile)
      }
    })
    comments
  }

  override fun equals(other: Any?) = (other as? GrUFile)?.psi == psi
}