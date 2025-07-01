// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.api.serialization.lookup.model

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiReference
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinNameSerializer
import org.jetbrains.kotlin.name.Name

@Serializable
@ApiStatus.Internal
sealed class UserDataValueModel {
    @Serializable
    data class BooleanModel(val value: Boolean) : UserDataValueModel()

    @Serializable
    data class StringModel(val value: String) : UserDataValueModel()

    @Serializable
    data class EnumModel(val ordinal: Int, val enumClass: String) : UserDataValueModel()

    @Serializable
    data class ClassModel(val className: String) : UserDataValueModel()

    @Serializable
    data class IntModel(val value: Int) : UserDataValueModel()

    @Serializable
    data class LongModel(val value: Long) : UserDataValueModel()

    @Serializable
    data class NameModel(
        @Serializable(with = KotlinNameSerializer::class) val name: Name
    ) : UserDataValueModel()

    @Serializable
    data class PsiReferenceModel(
        val element: PsiElementModel,
        val rangeInElementStart: Int,
        val rangeInElementEnd: Int,
        val referenceClass: String,
    ) : UserDataValueModel() {

        fun restore(project: Project): PsiReference? {
            val psiElement = element.restore(project) ?: return null
            for (reference in psiElement.references) {
                if (reference.javaClass.name != referenceClass) continue
                val rangeInElement = TextRange(rangeInElementStart, rangeInElementEnd)
                if (rangeInElement != reference.rangeInElement) continue
                return reference
            }
            return null
        }

        companion object {
            fun create(reference: PsiReference): PsiReferenceModel {
                return PsiReferenceModel(
                    PsiElementModel.create(reference.element),
                    reference.rangeInElement.startOffset,
                    reference.rangeInElement.endOffset,
                    reference.javaClass.name,
                )
            }
        }
    }
}
