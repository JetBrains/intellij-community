// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler

import org.jetbrains.annotations.Nls

/**
 * Exposing decompilation presets to the users was initially discussed in IDEA-343826.
 */
internal enum class DecompilerPreset(@Nls val description: String, val options: () -> Map<String, String>) {
  HIGH(IdeaDecompilerBundle.message("decompiler.preset.high.description"), { IdeaDecompilerSettings.getInstance().highPreset}),
  MEDIUM(IdeaDecompilerBundle.message("decompiler.preset.medium.description"), {IdeaDecompilerSettings.getInstance().mediumPreset}),
  LOW(IdeaDecompilerBundle.message("decompiler.preset.low.description"), {IdeaDecompilerSettings.getInstance().lowPreset});
}


