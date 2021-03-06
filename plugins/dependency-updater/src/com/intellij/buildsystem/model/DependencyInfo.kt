package com.intellij.buildsystem.model

open class DependencyInfo<T : BuildDependency>(
    open val dependency: T,
    open val metadata: BuildScriptEntryMetadata
)
