// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5Jimfs

import com.google.common.jimfs.Jimfs
import com.intellij.openapi.progress.blockingContext
import com.intellij.testFramework.junit5.resources.impl.ResourceExtension
import com.intellij.testFramework.junit5.resources.impl.ResourceExtensionImpl
import com.intellij.testFramework.junit5.resources.providers.ResourceProvider
import com.intellij.testFramework.junit5.resources.providers.ResourceStorage
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.FileSystem
import kotlin.reflect.KClass


/**
 * [Jimfs] is a memory-based [java.nio.file.Path]
 */
@TestOnly
class JimFsProvider : ResourceProvider<FileSystem> {
  override val resourceType: KClass<FileSystem> = FileSystem::class
  override suspend fun create(storage: ResourceStorage): FileSystem = Jimfs.newFileSystem()

  override val needsApplication: Boolean = false

  override suspend fun destroy(resource: FileSystem): Unit = blockingContext {
    resource.close()
  }
}

/**
 * ```
 * @ExtendWith(JimFsExtension::class)
 * ```
 * to get in-memory [FileSystem]
 * @see [ExtendWith]
 */
@TestOnly
class JimFsExtension : ResourceExtension<FileSystem, JimFsProvider> by ResourceExtensionImpl(JimFsProvider())

/**
 * To get in-memory [FileSystem] based in [Jimfs], use
 * ```kotlin
 * @JimFsResource
 * class MyTest {
 *   @Test
 *   fun foo(fs:FileSystem){}
 * }
 * ```
 */
@TestOnly
@Target(AnnotationTarget.CLASS)
@ExtendWith(JimFsExtension::class)
annotation class JimfsResource