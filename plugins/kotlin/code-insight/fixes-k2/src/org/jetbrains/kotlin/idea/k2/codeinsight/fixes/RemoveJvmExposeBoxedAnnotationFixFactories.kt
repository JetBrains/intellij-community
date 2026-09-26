// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
import org.jetbrains.kotlin.psi.KtAnnotationEntry

internal object RemoveJvmExposeBoxedAnnotationFixFactories {

    private fun removeAnnotation(psi: PsiElement): List<RemoveAnnotationFix> {
        val annotationEntry = psi as? KtAnnotationEntry ?: return emptyList()
        return listOf(
            RemoveAnnotationFix(KotlinBundle.message("remove.jvmexposeboxed.annotation"), annotationEntry)
        )
    }

    val uselessJvmExposeBoxed =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.UselessJvmExposeBoxed ->
            removeAnnotation(diagnostic.psi)
        }

    val cannotExposeSuspend =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCannotExposeSuspend ->
            removeAnnotation(diagnostic.psi)
        }

    val cannotExposeOpenAbstract =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCannotExposeOpenAbstract ->
            removeAnnotation(diagnostic.psi)
        }

    val cannotExposeSynthetic =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCannotExposeSynthetic ->
            removeAnnotation(diagnostic.psi)
        }

    val cannotExposeLocals =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCannotExposeLocals ->
            removeAnnotation(diagnostic.psi)
        }

    val cannotExposeReified =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCannotExposeReified ->
            removeAnnotation(diagnostic.psi)
        }

    val cannotExposePrivate =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedCannotExposePrivate ->
            removeAnnotation(diagnostic.psi)
        }

    /**
     * Secondary fix for [KaFirDiagnostic.JvmExposeBoxedRequiresName]: dropping the annotation is a
     * legitimate alternative to supplying a name (see [AddJvmExposeBoxedNameFixFactory]).
     */
    val requiresName =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.JvmExposeBoxedRequiresName ->
            removeAnnotation(diagnostic.psi)
        }
}
