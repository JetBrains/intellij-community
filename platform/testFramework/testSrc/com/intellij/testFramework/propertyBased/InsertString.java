/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework.propertyBased;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import slowCheck.Generator;

public class InsertString extends ActionOnRange {
  private final String myToInsert;

  InsertString(PsiFile file, int offset, String toInsert) {
    super(file, offset, offset);
    myToInsert = toInsert;
  }

  public static Generator<InsertString> asciiInsertions(@NotNull PsiFile psiFile) {
    return Generator.zipWith(Generator.integers(0, psiFile.getTextLength()).noShrink(), 
                             Generator.stringsOf(Generator.asciiPrintableChars()),
                             (offset, toInsert) -> new InsertString(psiFile, offset, toInsert));
  }

  @Override
  public String toString() {
    return "InsertString{" + getVirtualFile().getPath() + " " + getCurrentStartOffset() + " '" + myToInsert + "', raw=" + myInitialStart + "}";
  }

  public void performAction() {
    int offset = getFinalStartOffset();
    if (offset < 0) return;

    WriteCommandAction.runWriteCommandAction(getProject(), () -> getDocument().insertString(offset, myToInsert));
  }
}
