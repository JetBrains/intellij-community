// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.caches

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.indexing.DumbModeAccessType
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.stubindex.KotlinProbablyInjectedFunctionShortNameIndex

internal class ProbablyInjectedCallableNamesImpl(private val project: Project): ProbablyInjectedCallableNames {
    private val functionNames = CachedValuesManager.getManager(project).createCachedValue(
        {
            val allKeys = project.runReadActionInSmartMode { KotlinProbablyInjectedFunctionShortNameIndex.getAllKeys(project) }

            CachedValueProvider.Result.create(
                allKeys,
                KotlinModificationTrackerFactory.getInstance(project).createProjectWideSourceModificationTracker()
            )
        },
        false
    )

    override fun isProbablyInjectedCallableName(name: String): Boolean {
        return name in try {
            functionNames.value
        } catch (_: IndexNotReadyException) {
            DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
                KotlinProbablyInjectedFunctionShortNameIndex.getAllKeys(project)
            })
        }
    }

}