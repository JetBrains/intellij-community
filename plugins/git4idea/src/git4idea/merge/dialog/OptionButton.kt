// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge.dialog

import com.intellij.execution.ui.TagButton
import com.intellij.openapi.util.NlsSafe

internal class OptionButton<T>(val option: T,
                               @NlsSafe private val flag: String,
                               private val removeClickListener: () -> Unit) : TagButton(flag, { removeClickListener() })