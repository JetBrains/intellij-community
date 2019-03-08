// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.style;

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod


/**
 * @author knisht
 */
class InferMethodArguments : IntentionAction {
  override fun getText(): String {
    return GroovyIntentionsBundle.message("infer.method.arguments")
  }

  override fun getFamilyName(): String {
    return GroovyIntentionsBundle.message("infer.method.arguments.for.method.declaration")
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    if (editor == null || file == null) {
      return false
    }
    else {
      val offset = editor.caretModel.offset
      return findMethod(file, offset) != null
    }
  }

  fun findMethod(file: PsiFile, offset: Int): GrMethod? {
    val at = file.findElementAt(offset)
    val method = PsiTreeUtil.getParentOfType(at, GrMethod::class.java, false, GrTypeDefinition::class.java, GrClosableBlock::class.java)
    val textRange = method?.textRange
    if (textRange != null && (!textRange.contains(offset) && !textRange.contains(offset - 1))) {
      return null
    }
    val parameters = method?.parameters
    if (parameters != null && parameters.map(GrParameter::getTypeElement).any { it == null }) {
      return method
    }
    else {
      return null
    }

  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
  }

  override fun startInWriteAction(): Boolean {
    return false
  }

}

