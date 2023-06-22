// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.testFramework.PsiTestData;

/**
 * @deprecated {@link com.intellij.testFramework.EditorTestUtil.CaretAndSelectionState} instead
 */
public class CodeInsightTestData extends PsiTestData{
  public int LINE_NUMBER = -1;
  public int COLUMN_NUMBER = -1;
  public int SELECTION_START_LINE_NUMBER = -1;
  public int SELECTION_START_COLUMN_NUMBER = -1;
  public int SELECTION_END_LINE_NUMBER = -1;
  public int SELECTION_END_COLUMN_NUMBER = -1;

  public CodeInsightTestData() {
  }

  public int getLineNumber() {
    return LINE_NUMBER;
  }

  public int getColumnNumber() {
    return COLUMN_NUMBER;
  }

  public int getSelectionStartLineNumber() {
    return SELECTION_START_LINE_NUMBER;
  }

  public int getSelectionStartColumnNumber() {
    return SELECTION_START_COLUMN_NUMBER;
  }

  public int getSelectionEndLineNumber() {
    return SELECTION_END_LINE_NUMBER;
  }

  public int getSelectionEndColumnNumber() {
    return SELECTION_END_COLUMN_NUMBER;
  }
}
