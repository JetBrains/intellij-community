// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.conversions

import com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension
import com.intellij.psi.PsiElement
import com.intellij.util.Consumer
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

internal class ClassNameDiffersFromFileNamePredicate @JvmOverloads constructor(
  private val searchForClassInMultiClassFile: Boolean = false,
  private val classConsumer: Consumer<GrTypeDefinition>? = null,
  private val fileNameConsumer: Consumer<String>? = null
) : PsiElementPredicate {

  internal constructor(consumer: Consumer<GrTypeDefinition>?) : this(classConsumer = consumer)

  override fun satisfiedBy(element: PsiElement): Boolean {
    val clazz = element.parent as? GrTypeDefinition ?: return false
    if (clazz.nameIdentifierGroovy !== element) return false

    val className = clazz.name ?: return false
    if (className.isEmpty()) return false

    val file = clazz.parent as? GroovyFile ?: return false
    if (!file.isPhysical) return false

    val fileName = getNameWithoutExtension(file.name)
    if (fileName.isEmpty()) return false

    if (className == fileName) return false

    val result = if (searchForClassInMultiClassFile) file.classes.size > 1 else !file.isScript
    if (!result) return false

    classConsumer?.consume(clazz)
    fileNameConsumer?.consume(fileName)
    return true
  }
}
