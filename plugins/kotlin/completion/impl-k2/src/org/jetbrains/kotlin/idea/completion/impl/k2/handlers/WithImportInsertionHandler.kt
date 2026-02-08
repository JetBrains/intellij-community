// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinFqNameSerializer
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.doPostponedOperationsAndUnblockDocument
import org.jetbrains.kotlin.idea.completion.impl.k2.lookups.addImportIfRequired
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

@Serializable
internal data class WithImportInsertionHandler(
  @Serializable(with = FqNameListSerializer::class) val namesToImport: List<FqName>,
): SerializableInsertHandler {
    override fun handleInsert(
      context: InsertionContext,
      item: LookupElement
    ) {
        val targetFile = context.file
        if (targetFile !is KtFile) throw IllegalStateException("Target file '${targetFile.name}' is not a Kotlin file")

        for (nameToImport in namesToImport) {
          addImportIfRequired(context, nameToImport)
        }
        context.commitDocument()
        context.doPostponedOperationsAndUnblockDocument()
    }

    object FqNameListSerializer : KSerializer<List<FqName>> by ListSerializer(KotlinFqNameSerializer)
}