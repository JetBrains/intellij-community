// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.uast

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.uast.*
import java.util.*

class GrUFile(override val sourcePsi: GroovyFile, override val languagePlugin: UastLanguagePlugin) : UFile {
  override val psi: PsiFile
    get() = sourcePsi
  override val packageName: String
    get() = sourcePsi.packageName

  override val imports: List<UImportStatement> = emptyList() // not implemented

  override val uAnnotations: List<UAnnotation>
    get() = sourcePsi.packageDefinition?.annotationList?.annotations?.map { GrUAnnotation(it, { this }) } ?: emptyList()

  override val classes: List<GrUClass> by lazy { sourcePsi.classes.mapNotNull { (it as? GrTypeDefinition)?.let { GrUClass(it, { this }) } } }

  override val allCommentsInFile: ArrayList<UComment> by lazy {
    val comments = ArrayList<UComment>(0)
    sourcePsi.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitComment(comment: PsiComment) {
        comments += UComment(comment, this@GrUFile)
      }
    })
    comments
  }

  override fun equals(other: Any?): Boolean = (other as? GrUFile)?.sourcePsi == sourcePsi
  override fun hashCode(): Int = sourcePsi.hashCode()
}