// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.listCellRenderer.LcrIconInitParams
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
internal class LcrIconInitParamsImpl(accessibleName: @Nls String?) : LcrInitParamsImpl(accessibleName), LcrIconInitParams
