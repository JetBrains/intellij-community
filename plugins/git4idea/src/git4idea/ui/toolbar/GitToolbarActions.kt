// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.toolbar

import com.intellij.openapi.util.registry.Registry

object GitToolbarActions {
  internal fun isEnabledAndVisible(): Boolean {
    return Registry.`is`("vcs.new.ui.main.toolbar.actions")
  }
}
