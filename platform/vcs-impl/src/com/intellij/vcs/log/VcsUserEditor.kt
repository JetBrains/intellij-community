// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log

import com.intellij.openapi.project.Project
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor.StringValueDescriptor
import com.intellij.util.textCompletion.TextCompletionProvider
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.textCompletion.ValuesCompletionProvider.ValuesCompletionProviderDumbAware

private fun createCompletionProvider(values: List<String>): TextCompletionProvider =
  ValuesCompletionProviderDumbAware(StringValueDescriptor(), values)

class VcsUserEditor(project: Project, values: List<String>) :
  TextFieldWithCompletion(project, createCompletionProvider(values), "", true, true, true) {

  var user: VcsUser?
    get() = VcsUserParser.parse(project, text)
    set(value) = setText(value?.toString())

  override fun updateUI() {
    // When switching from Darcula to IntelliJ `getBackground()` has `UIUtil.getTextFieldBackground()` value which is `UIResource`.
    // `LookAndFeel.installColors()` (called from `updateUI()`) calls `setBackground()` and sets panel background (gray) to be used.
    // So we clear background to allow default behavior (use background from color scheme).
    background = null
    super.updateUI()
  }
}