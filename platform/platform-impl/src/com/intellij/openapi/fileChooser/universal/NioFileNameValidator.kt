// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.ide.IdeBundle
import com.intellij.openapi.ui.InputValidatorEx
import org.jetbrains.annotations.Nls
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Rejects a name that the [parent] file system cannot use for a direct child.
 *
 * The check asks [parent] to resolve the name, so each file system applies its own rules.
 * A backslash is a separator on Windows, and a plain character on a Linux container.
 *
 * The validator reads no file, because an input dialog calls it on the EDT for every keystroke.
 * A remote file system makes such a read too slow.
 */
internal class NioFileNameValidator(private val parent: Path) : InputValidatorEx {
  override fun getErrorText(inputString: String): @Nls String? {
    val name = inputString.trim()
    if (name.isEmpty()) return IdeBundle.message("universal.file.chooser.action.rename.error.empty")
    if (name == "." || name == "..") {
      return IdeBundle.message("universal.file.chooser.action.rename.error.invalid.name", name)
    }
    val resolved = try {
      parent.resolve(name)
    }
    catch (_: InvalidPathException) {
      return IdeBundle.message("universal.file.chooser.action.rename.error.invalid.name", name)
    }
    if (resolved.parent != parent || resolved.fileName?.toString() != name) {
      return IdeBundle.message("universal.file.chooser.action.rename.error.separator")
    }
    return null
  }
}
