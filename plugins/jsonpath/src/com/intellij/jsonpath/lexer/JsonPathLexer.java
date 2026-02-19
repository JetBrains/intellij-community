// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.lexer;

import com.intellij.lexer.FlexAdapter;

public final class JsonPathLexer extends FlexAdapter {
  public JsonPathLexer() {
    super(new _JsonPathLexer(null));
  }
}
