// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.project

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.indexing.DumbModeAccessType
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.stubindex.KotlinProbablyContractedFunctionShortNameIndex
import org.jetbrains.kotlin.resolve.lazy.ProbablyContractedCallableNames

class ProbablyContractedCallableNamesImpl(private val project: Project) : ProbablyContractedCallableNames {
    private val functionNames = CachedValuesManager.getManager(project).createCachedValue(
        {
            CachedValueProvider.Result.create(
                project.runReadActionInSmartMode { KotlinProbablyContractedFunctionShortNameIndex.getAllKeys(project) },
                KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker
            )
        },
        false
    )

    override fun isProbablyContractedCallableName(name: String): Boolean {
        return name in try {
            functionNames.value
        } catch (e: IndexNotReadyException) {
            DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(ThrowableComputable {
                KotlinProbablyContractedFunctionShortNameIndex.getAllKeys(project)
            })
        }
    }
}