package com.intellij.buildsystem.model.unified

import com.intellij.buildsystem.model.BuildDependencyRepository


data class UnifiedDependencyRepository(
    val id: String?,
    val name: String?,
    val url: String
) : BuildDependencyRepository