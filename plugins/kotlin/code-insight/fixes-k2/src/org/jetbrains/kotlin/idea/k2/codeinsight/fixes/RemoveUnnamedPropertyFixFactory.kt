// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object RemoveUnnamedPropertyFixFactory {
  val unnamedPropertyWithImplicitIgnorableTypeFixFactory =
    KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UnnamedPropertyWithImplicitIgnorableType ->
      val property = diagnostic.psi.getParentOfType<KtProperty>(strict = false) ?: return@ModCommandBased emptyList()
      if (property.name != "_" || property.initializer == null) return@ModCommandBased emptyList()
      listOf(RemoveUnnamedPropertyFix(property))
    }

  private class RemoveUnnamedPropertyFix(
    element: KtProperty,
  ) : KotlinPsiUpdateModCommandAction.ElementContextless<KtProperty>(element) {
    override fun getFamilyName(): @IntentionFamilyName String =
      KotlinBundle.message("remove.unused.underscore.declaration")

    override fun invoke(
      context: ActionContext,
      element: KtProperty,
      updater: ModPsiUpdater,
    ) {
      val initializer = element.initializer ?: return
      element.replace(initializer.copy())
    }
  }
}
