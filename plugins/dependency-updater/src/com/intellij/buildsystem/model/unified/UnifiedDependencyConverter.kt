package com.intellij.buildsystem.model.unified

import com.intellij.buildsystem.model.BuildDependency

interface UnifiedDependencyConverter<T : BuildDependency> {
    fun convert(buildDependency: T): UnifiedDependency
}
