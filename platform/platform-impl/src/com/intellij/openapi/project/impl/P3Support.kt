// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/**
 * Interface defining the P3 (Process Per Project) support.
 * This interface allows opening different projects in separate processes,
 * providing a mechanism to check if a project can be opened in the current process
 * and to open a project in a child process if necessary.
 */
@Experimental
interface P3Support {

  /**
   * Checks if the P3 support is enabled.
   *
   * @return true if P3 support is enabled, false otherwise.
   */
  fun isEnabled(): Boolean

  /**
   * Determines if the specified project can be opened in the current process.
   *
   * @param projectStoreBaseDir The base directory of the project store to check.
   * @return true if the project can be opened in the current process, false if it should be opened in a separate process.
   */
  fun canBeOpenedInThisProcess(projectStoreBaseDir: Path): Boolean

  /**
   * Opens the specified project in a child process. This method should be called only if [canBeOpenedInThisProcess] returns false.
   * Calling this method when [canBeOpenedInThisProcess] returns true may result in [UnsupportedOperationException].
   *
   * @param projectStoreBaseDir The base directory of the project store to open.
   * @throws UnsupportedOperationException if the operation is not supported or if the method is called inappropriately.
   */
  suspend fun openInChildProcess(projectStoreBaseDir: Path)
}

@Internal
@Experimental
object DisabledP3Support : P3Support {
  override fun isEnabled(): Boolean = false

  override fun canBeOpenedInThisProcess(projectStoreBaseDir: Path): Boolean = true

  override suspend fun openInChildProcess(projectStoreBaseDir: Path) {
    throw UnsupportedOperationException("Not supported")
  }
}

@Internal
@Experimental
object P3SupportInstaller {
  val atomicSupport = AtomicReference<P3Support?>()

  fun installPerProcessInstanceSupportImplementation(support: P3Support) {
    if (!atomicSupport.compareAndSet(null, support)) {
      throw IllegalStateException("${P3Support::class.qualifiedName} is already set: ${support}")
    }
  }

  fun seal() {
    atomicSupport.compareAndSet(null, DisabledP3Support)
  }
}

@Experimental
fun processPerProjectSupport(): P3Support {
  return P3SupportInstaller.atomicSupport.get() ?: run {
    logger<P3Support>().error("PerProcessInstanceSupportInstaller is not installed yet")

    DisabledP3Support
  }
}