// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.resources

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.impl.TestApplicationExtension
import com.intellij.testFramework.junit5.impl.TestApplicationLeakTrackerExtension
import com.intellij.testFramework.junit5.resources.impl.ResourceExtension
import com.intellij.testFramework.junit5.resources.impl.ResourceExtensionImpl
import com.intellij.testFramework.junit5.resources.providers.DisposableProvider
import com.intellij.testFramework.junit5.resources.providers.ProjectProvider
import com.intellij.testFramework.junit5.resources.providers.ResourceProvider
import com.intellij.testFramework.junit5.resources.providers.module.ModuleProvider
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith

/**
 * ```
 * @ExtendWith(DisposableExtension::class)
 * ```
 * to get [Disposable]
 * @see [ExtendWith]
 */
@TestOnly
class DisposableExtension : ResourceExtension<Disposable, DisposableProvider> by ResourceExtensionImpl(DisposableProvider())

/**
 * ```
 * @ExtendWith(ProjectExtension::class)
 * ```
 * to get [Project] with default params
 * @see [ExtendWith]
 */
@TestOnly
class ProjectExtension : ResourceExtension<Project, ProjectProvider> by ResourceExtensionImpl(ProjectProvider())

/**
 * ```
 * @ExtendWith(ModuleExtension::class)
 * ```
 * to get [Module] with default params
 * @see [ExtendWith]
 */
@TestOnly
class ModuleExtension : ResourceExtension<Module, ModuleProvider> by ResourceExtensionImpl(ModuleProvider())


/**
 * ```kotlin
 * @TestApplication
 * @DisposableResource
 * class MyTest {
 *   @Test
 *   fun foo(disposable:Disposable){}
 * }
 * ```
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(DisposableExtension::class)
annotation class DisposableResource

/**
 * ```kotlin
 * @TestApplication
 * @ProjectResource
 * class MyTest {
 *   @Test
 *   fun foo(project:Project){}
 * }
 * ```
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(ProjectExtension::class)
annotation class ProjectResource

/**
 * ```kotlin
 * @TestApplication
 * @ProjectResource
 * @ModuleResource
 * class MyTest {
 *   @Test
 *   fun foo(module:Module){}
 * }
 * ```
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(ModuleExtension::class)
annotation class ModuleResource

/**
 * Annotation that injects all resource extensions.
 * If in doubt, use approach good enough for most cases:
 * ```kotlin
 *@FullApplication
 * class MyClass {
 *    @Test
 *    fun myFun(project:Project, module:Module){}
 * }
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(TestApplicationExtension::class)
@ExtendWith(TestApplicationLeakTrackerExtension::class)
@ExtendWith(ProjectExtension::class)
@ExtendWith(ModuleExtension::class)
@ExtendWith(DisposableExtension::class)
annotation class FullApplication

/**
 * Resources are injected into fields by default. To prevent it, mark field with this interface
 * ```kotlin
 * @FullApplication
 * class MyClass {
 *   @NoInject // I do not want machinery to inject project here!
 *   var lateinit project:Project
 *   @Test
 *   fun myTest(){}
 * }
 *
 * ```
 */
@TestOnly
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
annotation class NoInject

/**
 * Creates JUnit5 extension from provider
 * Shortcut for [ResourceExtensionApi.forProvider]
 */
@TestOnly
inline fun <reified R : Any, reified RP : ResourceProvider<R>> RP.asExtension(): ResourceExtensionApi<R, RP> = ResourceExtensionApi.forProvider(this)