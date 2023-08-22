// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.gradleJava.configuration.kpm

import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmContentRoot
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmFragment
import org.jetbrains.kotlin.gradle.idea.kpm.IdeaKpmFragmentCoordinates
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext
import java.io.File


private val IdeaKpmFragmentCoordinates.fullName
    get() = fragmentName + (module.moduleName).capitalizeAsciiOnly()

private val IdeaKpmFragment.isTestFragment
    get() = coordinates.module.moduleName == "test"

internal fun calculateKotlinFragmentModuleId(
    gradleModule: IdeaModule,
    fragment: IdeaKpmFragmentCoordinates,
    resolverCtx: ProjectResolverContext
): String =
    GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule) + ":" + fragment.fullName

fun IdeaKpmFragment.computeSourceType(): ExternalSystemSourceType =
    if (isTestFragment) ExternalSystemSourceType.TEST else ExternalSystemSourceType.SOURCE

fun IdeaKpmFragment.computeResourceType(): ExternalSystemSourceType =
    if (isTestFragment) ExternalSystemSourceType.TEST_RESOURCE else ExternalSystemSourceType.RESOURCE

val IdeaKpmFragment.sourceDirs: Collection<File>
    get() = contentRoots.filter { it.type == IdeaKpmContentRoot.SOURCES_TYPE }.map { it.file }

val IdeaKpmFragment.resourceDirs: Collection<File>
    get() = contentRoots.filter { it.type == IdeaKpmContentRoot.RESOURCES_TYPE }.map { it.file }
