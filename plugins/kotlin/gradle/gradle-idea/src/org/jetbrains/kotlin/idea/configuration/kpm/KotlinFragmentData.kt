/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.configuration.kpm

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractNamedData
import com.intellij.serialization.PropertyMapping
import org.jetbrains.kotlin.gradle.KotlinFragmentResolvedDependency
import org.jetbrains.kotlin.gradle.KotlinLanguageSettings
import org.jetbrains.kotlin.gradle.KotlinPlatform
import org.jetbrains.plugins.gradle.util.GradleConstants

class KotlinFragmentData @PropertyMapping("externalName") constructor(externalName: String) :
    AbstractNamedData(GradleConstants.SYSTEM_ID, externalName) {
    var platform: KotlinPlatform = KotlinPlatform.COMMON
    val refinesFragmentIds: MutableSet<String> = hashSetOf()
    val resolvedFragmentDependencies: MutableSet<KotlinFragmentResolvedDependency> = hashSetOf()
    var languageSettings: KotlinLanguageSettings? = null

    companion object {
        val KEY = Key.create(KotlinFragmentData::class.java, ProjectKeys.MODULE.processingWeight + 1)
    }

}