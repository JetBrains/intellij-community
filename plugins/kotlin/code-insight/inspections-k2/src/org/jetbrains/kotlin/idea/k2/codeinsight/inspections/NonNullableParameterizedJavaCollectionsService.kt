// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.FqNameUnsafe

@Service(Service.Level.PROJECT)
internal class NonNullableParameterizedJavaCollectionsService() {

    private fun getCollections(tool: JavaCollectionWithNullableTypeArgumentInspection): Set<FqNameUnsafe> {
        return tool.nonNullableParameterizedJavaCollections
    }

    companion object {
        val DEFAULT: Set<String> = setOf(
            "java.util.concurrent.ConcurrentHashMap",
            "java.util.concurrent.ConcurrentSkipListMap",
            "java.util.concurrent.ConcurrentSkipListSet",
            "java.util.concurrent.ConcurrentLinkedQueue",
            "java.util.concurrent.ConcurrentLinkedDeque",
            "java.util.concurrent.ArrayBlockingQueue",
            "java.util.concurrent.BlockingQueue",
            "java.util.concurrent.LinkedTransferQueue",
            "java.util.concurrent.LinkedBlockingQueue",
            "java.util.concurrent.LinkedBlockingDeque",
            "java.util.concurrent.DelayQueue",
            "java.util.concurrent.PriorityBlockingQueue",
            "java.util.concurrent.SynchronousQueue",
            "java.util.concurrent.TransferQueue",
            "java.util.PriorityQueue"
        )

        fun getCollections(element: PsiElement): Set<FqNameUnsafe> {
            val project = element.project
            val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
            val tool = profile.getUnwrappedTool(JAVA_COLLECTION_WITH_NULLABLE_TYPE_ARGUMENT_INSPECTION, element)
            val collections = tool?.let {
                project.serviceOrNull<NonNullableParameterizedJavaCollectionsService>()?.getCollections(tool)
            } ?: DEFAULT.map(::FqNameUnsafe)
            return collections.toSet()
        }

        private val JAVA_COLLECTION_WITH_NULLABLE_TYPE_ARGUMENT_INSPECTION: Key<JavaCollectionWithNullableTypeArgumentInspection> =
            Key.create("JavaCollectionWithNullableTypeArgument")
    }
}