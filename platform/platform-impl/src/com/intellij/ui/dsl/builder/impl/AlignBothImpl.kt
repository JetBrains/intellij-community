// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.ui.dsl.builder.AlignBoth
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class AlignBothImpl(val alignX: AlignX, val alignY: AlignY) : AlignBoth
