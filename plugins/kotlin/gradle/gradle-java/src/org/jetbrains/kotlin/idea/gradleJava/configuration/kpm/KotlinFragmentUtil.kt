// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.projectModel.KotlinFragment
import org.jetbrains.kotlin.idea.projectModel.KotlinModule
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext


private val KotlinFragment.fullName
    get() = fragmentName + (moduleIdentifier.moduleClassifier ?: KotlinModule.MAIN_MODULE_NAME).capitalizeAsciiOnly()

internal fun calculateKotlinFragmentModuleId(
    gradleModule: IdeaModule,
    fragment: KotlinFragment,
    resolverCtx: ProjectResolverContext
): String =
    GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule) + ":" + fragment.fullName

fun KotlinFragment.computeSourceType(): ExternalSystemSourceType =
    if (isTestFragment) ExternalSystemSourceType.TEST else ExternalSystemSourceType.SOURCE

fun KotlinFragment.computeResourceType(): ExternalSystemSourceType =
    if (isTestFragment) ExternalSystemSourceType.TEST_RESOURCE else ExternalSystemSourceType.RESOURCE

