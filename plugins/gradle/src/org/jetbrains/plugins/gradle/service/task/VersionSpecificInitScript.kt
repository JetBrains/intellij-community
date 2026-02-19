// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.task

import org.gradle.util.GradleVersion
import java.util.function.Supplier

interface VersionSpecificInitScript {
  val script: String
  val filePrefix: String?
  val isApplicable: (GradleVersion) -> Boolean
  fun isApplicableTo(ver: GradleVersion) = isApplicable(ver)
}

class LazyVersionSpecificInitScript(scriptSupplier: Supplier<String>,
                                    override val filePrefix: String? = null,
                                    override val isApplicable: (GradleVersion) -> Boolean) : VersionSpecificInitScript {
  private val lazyScript by lazy(LazyThreadSafetyMode.NONE) { scriptSupplier.get() }

  override val script: String
    get() = lazyScript
}

data class PredefinedVersionSpecificInitScript(override val script: String,
                                               override val filePrefix: String? = null,
                                               override val isApplicable: (GradleVersion) -> Boolean) : VersionSpecificInitScript
