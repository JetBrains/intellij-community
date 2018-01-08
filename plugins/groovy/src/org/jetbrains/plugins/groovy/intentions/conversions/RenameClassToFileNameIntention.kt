/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.intentions.conversions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringFactory
import com.intellij.util.Consumer
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle.message
import org.jetbrains.plugins.groovy.intentions.base.Intention
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate

class RenameClassToFileNameIntention : Intention() {

  private lateinit var myFileName: String

  override fun getElementPredicate(): PsiElementPredicate = ClassNameDiffersFromFileNamePredicate(
    fileNameConsumer = Consumer { fileName ->
      myFileName = fileName
    }
  )

  override fun isStopElement(element: PsiElement?): Boolean = true

  override fun getText(): String = message("rename.class.to.0", myFileName)

  override fun startInWriteAction(): Boolean = false

  override fun processIntention(element: PsiElement, project: Project, editor: Editor?) {
    var clazz: PsiClass? = null
    val predicate = ClassNameDiffersFromFileNamePredicate(classConsumer = Consumer { clazz = it })
    if (!predicate.satisfiedBy(element)) return
    RefactoringFactory.getInstance(project).createRename(clazz!!, myFileName).run()
  }
}
