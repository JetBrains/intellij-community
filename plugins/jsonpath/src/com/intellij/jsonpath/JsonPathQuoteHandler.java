// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.jsonpath.psi.JsonPathTypes;

public final class JsonPathQuoteHandler extends SimpleTokenSetQuoteHandler {
  public JsonPathQuoteHandler() {
    super(JsonPathTypes.SINGLE_QUOTED_STRING, JsonPathTypes.DOUBLE_QUOTED_STRING);
  }
}
