// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.comment;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

public class ChangeToEndOfLineCommentIntentionTest extends IPPTestCase {

  public void testTrimLines() { doTest(); }
  public void testConvertMultiLineTodo() { doTest(); }
  public void testConvertMultiLineTodo2() { doTest(); }
  public void testEmptyLines() { doTest(); }
  public void testSingleLineBlockAtEof() { doTest(); }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("change.to.end.of.line.comment.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "comment/to_end_of_line";
  }
}