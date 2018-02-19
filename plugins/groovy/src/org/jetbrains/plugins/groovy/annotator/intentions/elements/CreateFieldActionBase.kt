// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.lang.jvm.actions.JvmGroupIntentionAction
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition

internal abstract class CreateFieldActionBase(
  target: GrTypeDefinition,
  override val request: CreateFieldRequest
) : CreateMemberAction(target, request), JvmGroupIntentionAction {

  override fun getRenderData() = JvmActionGroup.RenderData { request.fieldName }

  override fun getFamilyName(): String = message("create.field.from.usage.family")
}
