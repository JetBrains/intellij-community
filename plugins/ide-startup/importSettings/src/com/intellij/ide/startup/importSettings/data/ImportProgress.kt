// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.IPropertyView
import org.jetbrains.annotations.Nls

interface ImportProgress {
  val progressMessage: IPropertyView<@Nls String?>
  val progress: IOptPropertyView<Int>
}