// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.core.ec4jwrappers

import org.ec4j.core.parser.ErrorEvent
import org.ec4j.core.parser.ErrorHandler
import org.ec4j.core.parser.ParseContext
import org.ec4j.core.parser.ParseException

class EditorConfigLoadErrorHandler : ErrorHandler {
  override fun error(context: ParseContext, errorEvent: ErrorEvent) {
    if (errorEvent.errorType.isSyntaxError && errorEvent.errorType != ErrorEvent.ErrorType.PROPERTY_VALUE_MISSING) {
      throw ParseException(errorEvent)
    }
  }
}