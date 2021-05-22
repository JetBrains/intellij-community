// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.ui.SimpleColoredComponent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.ui.util.GHUIUtil

@Deprecated("Moved to proper package", replaceWith = ReplaceWith("GHUIUtil", "org.jetbrains.plugins.github.ui.util.GHUIUtil"))
@ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
object GithubUIUtil {
  fun createNoteWithAction(action: () -> Unit): SimpleColoredComponent = GHUIUtil.createNoteWithAction(action)
}
