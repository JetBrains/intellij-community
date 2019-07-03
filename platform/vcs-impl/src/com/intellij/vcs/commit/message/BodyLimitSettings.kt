// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit.message

internal class BodyLimitSettings(
  val rightMargin: Int,
  val isShowRightMargin: Boolean,
  val isWrapOnTyping: Boolean
)