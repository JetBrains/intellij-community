// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.execution;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Used to safely pass command argument. It handles quoting, escaping.
 */
public interface CommandLineArgumentEncoder {
  CommandLineArgumentEncoder DEFAULT_ENCODER = new CommandLineArgumentEncoder() {
    @Override
    public void encodeArgument(@NotNull StringBuilder builder) {
      StringUtil.escapeQuotes(builder);
      if (builder.length() == 0 || StringUtil.indexOf(builder, ' ') >= 0 || StringUtil.indexOf(builder, '|') >= 0) {
        // don't let a trailing backslash (if any) unintentionally escape the closing quote
        int numTrailingBackslashes = builder.length() - StringUtil.trimTrailing(builder, '\\').length();
        StringUtil.quote(builder);
        StringUtil.repeatSymbol(builder, '\\', numTrailingBackslashes);
      }
    }
  };

  void encodeArgument(@NotNull StringBuilder argument);
}