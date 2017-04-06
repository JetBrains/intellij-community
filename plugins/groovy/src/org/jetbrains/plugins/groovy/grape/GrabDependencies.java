/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.grape;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

/**
 * @author a.afanasiev
 */
public class GrabDependencies implements IntentionAction {
  @Override
  @NotNull
  public String getText() {
    return "Grab the artifacts";
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return "Grab";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {

    int offset = editor.getCaretModel().getOffset();
    final GrAnnotation anno = PsiTreeUtil.findElementOfClassAtOffset(file, offset, GrAnnotation.class, false);
    if (anno != null && isGrabAnnotation(anno)) {
      return true;
    }

    return false;
  }

  private static boolean isGrabAnnotation(@NotNull GrAnnotation anno) {
    final String qname = anno.getQualifiedName();
    return qname != null && (qname.startsWith(GrabAnnos.GRAB_ANNO) || GrabAnnos.GRAPES_ANNO.equals(qname));
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    new GrabStartupActivity().runActivity(project);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
