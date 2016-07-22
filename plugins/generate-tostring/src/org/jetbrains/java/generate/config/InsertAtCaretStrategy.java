/*
 * Copyright 2001-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.config;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.PsiGenerationInfo;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Inserts the method at the caret position.
 */
public class InsertAtCaretStrategy implements InsertNewMethodStrategy {

  private static final InsertAtCaretStrategy instance = new InsertAtCaretStrategy();

  private InsertAtCaretStrategy() {}

  public static InsertAtCaretStrategy getInstance() {
    return instance;
  }

  public PsiMethod insertNewMethod(PsiClass clazz, @NotNull PsiMethod newMethod, Editor editor) {
    int offset = (editor != null) ? editor.getCaretModel().getOffset() : (clazz.getTextRange().getEndOffset() - 1);
    final PsiGenerationInfo<PsiMethod> generationInfo = new PsiGenerationInfo<>(newMethod, false);
    GenerateMembersUtil.insertMembersAtOffset(clazz.getContainingFile(), offset, Arrays.asList(generationInfo));
    return generationInfo.getPsiMember();
  }

  public String toString() {
    return "At caret";
  }
}
