// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.assertj

import com.intellij.openapi.util.UserDataHolderBase
import org.assertj.core.configuration.Configuration
import org.assertj.core.presentation.Representation
import org.assertj.core.presentation.StandardRepresentation

/**
 * This is a workaround for [#2192](https://github.com/assertj/assertj-core/issues/2192). Without it instances of classes inheriting from
 * `UserDataHolderBase` will be shown as `AtomicReference[...]`.
 */
class PatchedConfiguration : Configuration() {
  override fun representation(): Representation {
    return object : StandardRepresentation() {
      override fun toStringOf(o: Any?): String? {
        if (o is UserDataHolderBase) {
          return o.toString()
        }
        return super.toStringOf(o)
      }
    }
  }

  override fun applyAndDisplay() {
    apply()
  }
}