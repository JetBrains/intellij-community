// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.AbstractNamedData
import com.intellij.serialization.PropertyMapping
import org.jetbrains.kotlin.idea.projectModel.KotlinFragmentResolvedDependency
import org.jetbrains.kotlin.idea.projectModel.KotlinLanguageSettings
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
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