// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test.matcher

import org.jetbrains.kotlin.gradle.Reporter
import org.jetbrains.kotlin.test.domain.ModuleEntity
import org.jetbrains.kotlin.test.domain.ProjectEntity

class ProjectMatcher(
    val projectEntity: ProjectEntity,
    val reporter: Reporter,
    internal val exhaustiveModuleList: Boolean,
    internal val exhaustiveSourceSourceRootList: Boolean,
    internal val exhaustiveDependencyList: Boolean,
    internal val exhaustiveTestsList: Boolean
) : MatcherModel() {
    override fun report(message: String) {
        reporter.report(message)
    }

    var allModulesAsserter: (ModuleMatcher.() -> Unit)? = null

    fun allModules(body: ModuleMatcher.() -> Unit) {
        assert(allModulesAsserter == null)
        allModulesAsserter = body
    }

    fun module(name: String, isOptional: Boolean = false, matcherFunc: ModuleMatcher.() -> Unit = {}) {
        val module = projectEntity.moduleManager.findModuleByName(name)
        if (module == null) {
            if (!isOptional) {
                reporter.report("No module found: '$name' in ${projectEntity.modules?.map { it.name }}")
            }
            return
        }
        ModuleMatcher(ModuleEntity.fromOpenapiModule(module), reporter).apply { matcherFunc() }
    }

}

fun checkProjectEntity(
    projectEntity: ProjectEntity,
    reporter: Reporter,
    exhaustiveModuleList: Boolean,
    exhaustiveSourceSourceRootList: Boolean,
    exhaustiveDependencyList: Boolean,
    exhaustiveTestsList: Boolean,
    checkFunc: ProjectMatcher.() -> Unit
) {

    ProjectMatcher(
        projectEntity,
        reporter,
        exhaustiveModuleList,
        exhaustiveSourceSourceRootList,
        exhaustiveDependencyList,
        exhaustiveTestsList
    ).apply(checkFunc)

    reporter.check()
}

