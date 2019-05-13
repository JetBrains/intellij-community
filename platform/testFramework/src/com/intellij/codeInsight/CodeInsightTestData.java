/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.testFramework.PsiTestData;

/**
 * @author Mike
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
