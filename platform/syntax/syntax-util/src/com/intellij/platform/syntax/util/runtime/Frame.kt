// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.runtime

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface Frame {

  val parentFrame: Frame?

  var elementType: SyntaxElementType?  // Left public because this field is modified extensively in SqlParser

  val offset: Int

  val position: Int

  val level: Int

  var modifiers: Modifiers  // Left public because this field can be modified in many different ways

  val name: @NonNls String?

  val variantCount: Int

  val errorReportedAt: Int

  val lastVariantAt: Int

  val leftMarker: SyntaxTreeBuilder.Marker?

  fun resetErrorReportedAt()
}