/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.editor.actions.moveUpDown;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class DeclarationMover extends LineMover {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.editor.actions.moveUpDown.DeclarationMover");

  public DeclarationMover(final boolean isDown) {
    super(isDown);
  }

  protected void beforeMove(final Editor editor) {
    super.beforeMove(editor);
  }

  protected void afterMove(final Editor editor, final PsiFile file) {
    super.afterMove(editor, file);
  }

  protected boolean checkAvailable(Editor editor, PsiFile file) {
    if (!(file instanceof GroovyFile)) {
      return false;
    }
    boolean available = super.checkAvailable(editor, file);
    if (!available) return false;
    LineRange oldRange = toMove;
    final Pair<PsiElement, PsiElement> psiRange = getElementRange(editor, file, oldRange);
    if (psiRange == null) return false;

    PsiElement first = psiRange.getFirst();
    first = PsiUtil.isNewLine(first) ? first.getNextSibling() : first;
    PsiElement firstMember = PsiTreeUtil.getParentOfType(first, GrMember.class, false);
    if (firstMember == null) firstMember = PsiTreeUtil.getParentOfType(first, GrTypeDefinition.class, false);

    PsiElement second = psiRange.getSecond();
    second = PsiUtil.isNewLine(second) ? second.getPrevSibling() : second;
    PsiElement lastMember = PsiTreeUtil.getParentOfType(second, GrMember.class, false);
    if (lastMember == null) lastMember = PsiTreeUtil.getParentOfType(first, GrTypeDefinition.class, false);

    if (firstMember == null || lastMember == null) return false;

    LineRange range;
    if (firstMember == lastMember) {
      range = memberRange(firstMember, editor, oldRange);
      if (range == null) return false;
      range.firstElement = range.lastElement = firstMember;
    } else {
      final PsiElement parent = PsiTreeUtil.findCommonParent(firstMember, lastMember);
      if (parent == null) return false;

      final Pair<PsiElement, PsiElement> combinedRange = getElementRange(parent, firstMember, lastMember);
      if (combinedRange == null) return false;
      final LineRange lineRange1 = memberRange(combinedRange.getFirst(), editor, oldRange);
      if (lineRange1 == null) return false;
      final LineRange lineRange2 = memberRange(combinedRange.getSecond(), editor, oldRange);
      if (lineRange2 == null) return false;
      range = new LineRange(lineRange1.startLine, lineRange2.endLine);
      range.firstElement = combinedRange.getFirst();
      range.lastElement = combinedRange.getSecond();
    }
    Document document = editor.getDocument();

    PsiElement sibling = isDown ? range.lastElement.getNextSibling() : range.firstElement.getPrevSibling();
    try {
      if (sibling == null) throw new IllegalMoveException();
      sibling = firstNonWhiteElement(sibling, isDown);
      final boolean areWeMovingClass = range.firstElement instanceof GrTypeDefinition;
      toMove = range;

      LineRange intraClassRange = moveInsideOutsideClassPosition(editor, sibling, isDown, areWeMovingClass);
      if (intraClassRange == null) {
        toMove2 = new LineRange(sibling, sibling, document);
//        if (isDown && sibling.getNextSibling() == null) return false;
      } else {
        toMove2 = intraClassRange;
      }
    }
    catch (IllegalMoveException e) {
      toMove2 = null;
    }
    return true;
  }

  private static LineRange memberRange(@NotNull PsiElement member, Editor editor, LineRange lineRange) {
    final TextRange textRange = member.getTextRange();
    if (editor.getDocument().getTextLength() < textRange.getEndOffset()) return null;
    final int startLine = editor.offsetToLogicalPosition(textRange.getStartOffset()).line;
    final int endLine = editor.offsetToLogicalPosition(textRange.getEndOffset()).line + 1;
    if (!isInsideDeclaration(member, startLine, endLine, lineRange, editor)) return null;

    return new LineRange(startLine, endLine);
  }

  private static boolean isInsideDeclaration(@NotNull final PsiElement member,
                                             final int startLine,
                                             final int endLine,
                                             final LineRange lineRange,
                                             final Editor editor) {
    // if we positioned on member start or end we'll be able to move it
    if (startLine == lineRange.startLine || startLine == lineRange.endLine || endLine == lineRange.startLine ||
        endLine == lineRange.endLine) {
      return true;
    }
    List<PsiElement> memberSuspects = new ArrayList<PsiElement>();
    GrModifierList modifierList = member instanceof GrMember ? ((GrMember) member).getModifierList() : null;
    if (modifierList != null) memberSuspects.add(modifierList);
    if (member instanceof GrMethod) {
      final GrMethod method = (GrMethod) member;
      PsiElement nameIdentifier = method.getNameIdentifierGroovy();
      memberSuspects.add(nameIdentifier);
      GrTypeElement returnTypeElement = method.getReturnTypeElementGroovy();
      if (returnTypeElement != null) memberSuspects.add(returnTypeElement);
    }
    if (member instanceof GrField) {
      final GrField field = (GrField) member;
      PsiElement nameIdentifier = field.getNameIdentifierGroovy();
      memberSuspects.add(nameIdentifier);
      GrTypeElement typeElement = field.getTypeElementGroovy();
      if (typeElement != null) memberSuspects.add(typeElement);
    }
    TextRange lineTextRange = new TextRange(editor.getDocument().getLineStartOffset(lineRange.startLine), editor.getDocument().getLineEndOffset(lineRange.endLine));
    for (PsiElement suspect : memberSuspects) {
      TextRange textRange = suspect.getTextRange();
      if (textRange != null && lineTextRange.intersects(textRange)) return true;
    }
    return false;
  }

  private static class IllegalMoveException extends Exception {
  }

  // null means we are not crossing class border
  // throws IllegalMoveException when corresponding movement has no sense
  @Nullable
  private LineRange moveInsideOutsideClassPosition(Editor editor, PsiElement sibling, final boolean isDown, boolean areWeMovingClass) throws IllegalMoveException {
    if (sibling == null) throw new IllegalMoveException();
    if (sibling.getNode() != null &&
        sibling.getNode().getElementType() == (isDown ? GroovyTokenTypes.mRCURLY : GroovyTokenTypes.mLCURLY) &&
        sibling.getParent() instanceof GrTypeDefinitionBody) {
      // moving outside class
      final GrTypeDefinition aClass = (GrTypeDefinition) sibling.getParent().getParent();
      final PsiElement parent = aClass.getParent();
      if (!areWeMovingClass && !(parent instanceof GrTypeDefinitionBody)) throw new IllegalMoveException();
      PsiElement start = isDown ? sibling : aClass.getModifierList();
      if (start == null) throw new IllegalMoveException();
      return new LineRange(start, sibling, editor.getDocument());
    }

    // moving inside class not supported in Groovy
    /*if (sibling instanceof GrTypeDefinition) {
      GrTypeDefinition aClass = (GrTypeDefinition) sibling;
      GrTypeDefinitionBody body = aClass.getBody();
      if (body != null) {
        if (isDown && body.getLBrace() != null) {
          PsiElement child = aClass.getFirstChild();
          if (child == null) throw new IllegalMoveException();
          return new LineRange(child, body.getLBrace(), editor.getDocument());
        } else {
          PsiElement rBrace = body.getRBrace();
          if (rBrace == null) throw new IllegalMoveException();
          return new LineRange(rBrace, rBrace, editor.getDocument());
        }
      } else {
        throw new IllegalMoveException();
      }
    }*/

    return null;
  }

}
