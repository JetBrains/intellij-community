// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.NlsSafe

class GHLabel(id: String,
              val url: String,
              @NlsSafe val name: String,
              val color: String)
  : GHNode(id) {

  companion object {
    @VisibleForTesting
    internal fun createTest(id: String) = GHLabel(id, "", "testLabel_$id", "#FFFFFF")
  }
}
