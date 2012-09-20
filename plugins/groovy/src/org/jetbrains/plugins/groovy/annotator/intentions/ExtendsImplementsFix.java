/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class ExtendsImplementsFix implements IntentionAction {
  public static final ExtendsImplementsFix MOVE_TO_EXTENDS_LIST = new ExtendsImplementsFix(true);
  public static final ExtendsImplementsFix MOVE_TO_IMPLEMENTS_LIST = new ExtendsImplementsFix(false);

  private final boolean moveToExtendsList;

  private ExtendsImplementsFix(boolean moveToExtendsList) {
    this.moveToExtendsList = moveToExtendsList;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message(moveToExtendsList ? "move.to.extends.list" : "move.to.implements.list");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return file.findReferenceAt(editor.getCaretModel().getOffset()) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiReference ref = file.findReferenceAt(editor.getCaretModel().getOffset());

    PsiElement element = ref.getElement();
    while (element.getParent() instanceof GrCodeReferenceElement) element = element.getParent();
    final GrReferenceList oldRefList = ((GrReferenceList)element.getParent());
    final GrTypeDefinition classParent = ((GrTypeDefinition)oldRefList.getParent());
    GrReferenceList refList = moveToExtendsList ? classParent.getExtendsClause() : classParent.getImplementsClause();
    if (refList == null) {
      final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
      if (moveToExtendsList) {
        refList = factory.createExtendsClause();
        refList = (GrReferenceList)classParent.addAfter(refList, classParent.getNameIdentifierGroovy());
      }
      else {
        refList = factory.createImplementsClause();
        refList = (GrReferenceList)classParent.addAfter(refList, classParent.getExtendsClause());
      }
    }
    refList.add(element.copy());

    if (oldRefList.getReferenceElements().length==1) {
      oldRefList.delete();
    }
    else {
      final PsiElement prev = PsiUtil.skipWhitespacesAndComments(element.getPrevSibling(), false);
      if (prev != null && prev.getNode().getElementType().equals(GroovyTokenTypes.mCOMMA)) {
        prev.delete();
      }
      else {
        final PsiElement next = PsiUtil.skipWhitespacesAndComments(element.getNextSibling(), false);
        if (next != null && next.getNode().getElementType().equals(GroovyTokenTypes.mCOMMA)) {
          next.delete();
        }
      }
      element.delete();
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
