// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.statistics.InspectionData
import org.jetbrains.kotlin.idea.statistics.LanguageFeatureDeprecationCollector

/**
 * An inspection that can produce deprecation data about the code it is operating on.
 * The [defaultDeprecationData] needs to be provided for the functionality to function,
 * but it can be left null to disable any statistics from being gathered or collected.
 */
abstract class DeprecationCollectingInspection<T : InspectionData>(
    private val collector: LanguageFeatureDeprecationCollector<T>? = null,
    private val defaultDeprecationData: T? = null
) : AbstractKotlinInspection() {
    private val deprecationDataKey = Key.create<T>("deprecation_data")

    private val LocalInspectionToolSession.deprecationData
        get() = getUserData(deprecationDataKey)

    private fun LocalInspectionToolSession.isFullInspection(): Boolean {
        return priorityRange == file.textRange && restrictRange == file.textRange
    }

    protected fun LocalInspectionToolSession.updateDeprecationData(f: (T) -> T) {
        val existingData = deprecationData ?: return
        putUserData(deprecationDataKey, f(existingData))
    }

    override fun inspectionStarted(session: LocalInspectionToolSession, isOnTheFly: Boolean) {
        if (!session.isFullInspection()) return
        defaultDeprecationData?.let {
            session.putUserData(deprecationDataKey, it)
        }
    }

    override fun inspectionFinished(session: LocalInspectionToolSession, problemsHolder: ProblemsHolder) {
        if (!session.isFullInspection()) return
        val data = session.deprecationData ?: defaultDeprecationData ?: return
        reportDeprecationData(data, problemsHolder.file)
    }

    open fun reportDeprecationData(inspectionData: T, file: PsiFile) {
        collector?.logInspectionUpdated(file, inspectionData, file.languageVersionSettings.languageVersion)
    }
}