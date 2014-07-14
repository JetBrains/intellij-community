/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser;

/**
* Created by Max Medvedev on 29/01/14
*/
public class IntroduceOccurrencesChooser extends OccurrencesChooser<Object> {
  public IntroduceOccurrencesChooser(Editor editor) {
    super(editor);
  }

  @Override
  protected TextRange getOccurrenceRange(Object occurrence) {
    if (occurrence instanceof PsiElement) {
      return ((PsiElement)occurrence).getTextRange();
    }
    else if (occurrence instanceof StringPartInfo) {
      return ((StringPartInfo)occurrence).getRange();
    }
    else {
      return null;
    }
  }
}
