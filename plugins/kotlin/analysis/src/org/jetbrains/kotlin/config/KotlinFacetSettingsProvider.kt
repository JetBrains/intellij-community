// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.config

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

interface KotlinFacetSettingsProvider {
    fun getSettings(module: Module): KotlinFacetSettings?
    fun getInitializedSettings(module: Module): KotlinFacetSettings

    companion object {
        fun getInstance(project: Project): KotlinFacetSettingsProvider? = project.takeUnless(Project::isDisposed)
            ?.getService(KotlinFacetSettingsProvider::class.java)
    }
}
