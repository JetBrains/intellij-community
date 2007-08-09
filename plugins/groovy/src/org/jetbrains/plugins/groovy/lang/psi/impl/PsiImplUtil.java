/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrAdditiveExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrMultiplicativeExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrPowerExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrShiftExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex.GrRegexExprImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrEqualityExprImpl;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.refactoring.GroovyVariableUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil");

  public static GrExpression replaceExpression(GrExpression oldExpr, GrExpression newExpr) throws IncorrectOperationException {
    ASTNode oldNode = oldExpr.getNode();
    ASTNode parentNode = oldExpr.getParent().getNode();
    if (oldExpr.getParent() == null ||
        parentNode == null) {
      throw new IncorrectOperationException();
    }
    // Remove unnecessary parentheses
    if (oldExpr.getParent() instanceof GrParenthesizedExpr &&
        getExprPriorityLevel(newExpr) == 0) {
      return ((GrExpression) oldExpr.getParent()).replaceWithExpression(newExpr);
    }

    // check priorities
    GroovyElementFactory factory = GroovyElementFactory.getInstance(oldExpr.getProject());
    if (oldExpr.getParent() instanceof GrExpression) {
      GrExpression parentExpr = (GrExpression) oldExpr.getParent();
      int parentPriorityLevel = getExprPriorityLevel(parentExpr);
      int newPriorityLevel = getExprPriorityLevel(newExpr);
      if (parentPriorityLevel > newPriorityLevel) {
        newExpr = factory.createParenthesizedExpr(newExpr);
      } else if (parentPriorityLevel == newPriorityLevel && parentPriorityLevel != 0) {
        if (parentExpr instanceof GrBinaryExpression) {
          GrBinaryExpression binaryExpression = (GrBinaryExpression) parentExpr;
          if (isNotAssociative(binaryExpression) &&
              oldExpr.equals(binaryExpression.getRightOperand())) {
            newExpr = factory.createParenthesizedExpr(newExpr);
          }
        }
      }
    }

    ASTNode newNode = newExpr.getNode();
    parentNode.replaceChild(oldNode, newNode);
    if (!(newNode.getPsi() instanceof GrExpression)) {
      throw new IncorrectOperationException();
    }
    return ((GrExpression) newNode.getPsi());
  }

  private static boolean isNotAssociative(GrBinaryExpression binaryExpression) {
    if (binaryExpression instanceof GrMultiplicativeExprImpl) {
      return binaryExpression.getOperationTokenType() != GroovyTokenTypes.mSTAR;
    }
    if (binaryExpression instanceof GrAdditiveExprImpl) {
      return binaryExpression.getOperationTokenType() == GroovyTokenTypes.mMINUS;
    }
    return binaryExpression instanceof GrEqualityExprImpl
        || binaryExpression instanceof GrRegexExprImpl
        || binaryExpression instanceof GrShiftExprImpl
        || binaryExpression instanceof GrPowerExprImpl;
  }

  public static void shortenReferences(GroovyPsiElement element) {
    doShorten(element);
  }

  private static void doShorten(PsiElement element) {
    PsiElement child = element.getFirstChild();
    while (child != null) {
      if (child instanceof GrReferenceElement) {
        final GrCodeReferenceElement ref = (GrCodeReferenceElement) child;
        if (ref.getQualifier() != null) {
          final PsiElement resolved = ref.resolve();
          if (resolved instanceof PsiClass) {
            ref.setQualifier(null);
            try {
              ref.bindToElement(resolved);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }

      doShorten(child);
      child = child.getNextSibling();
    }
  }

  public static SearchScope getUseScope(GrMember member) {
    if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
      return new LocalSearchScope(member.getContainingFile()); //todo: what does GLS say?
    }

    return member.getManager().getSearchHelper().getUseScope(member);
  }

  public static PsiNamedElement[] getMethodVariants(GrReferenceElement methodReference) {
    final GroovyResolveResult[] results = methodReference.getSameNameVariants(); //will ignore argument types
    List<PsiNamedElement> elements = new ArrayList<PsiNamedElement>();
    for (GroovyResolveResult result : results) {
      final PsiElement element = result.getElement();
      if (element instanceof PsiNamedElement) elements.add((PsiNamedElement) element);
    }

    return elements.toArray(new PsiNamedElement[elements.size()]);
  }

  public static GrExpression getRuntimeQualifier(GrReferenceExpression refExpr) {
    GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      GrClosableBlock closure = PsiTreeUtil.getParentOfType(refExpr, GrClosableBlock.class);
      while (closure != null) {
        GrExpression funExpr = null;
        PsiElement parent = closure.getParent();
        if (parent instanceof GrApplicationStatement) {
          funExpr = ((GrApplicationStatement) parent).getFunExpression();
        } else if (parent instanceof GrMethodCallExpression) {
          funExpr = ((GrMethodCallExpression) parent).getInvokedExpression();
        }
        if (funExpr instanceof GrReferenceExpression) {
          qualifier = ((GrReferenceExpression) funExpr).getQualifierExpression();
          if (qualifier != null) break;
        } else break;

        closure = PsiTreeUtil.getParentOfType(closure, GrClosableBlock.class);
      }
    }

    return qualifier;
  }

  public static void removeVariable(GrVariable variable) throws IncorrectOperationException {
    final GrVariableDeclaration varDecl = (GrVariableDeclaration) variable.getParent();
    final List<GrVariable> variables = Arrays.asList(varDecl.getVariables());
    if (!variables.contains(variable)) {
      throw new IncorrectOperationException();
    }

    final ASTNode varDeclNode = varDecl.getNode();
    final PsiElement parent = varDecl.getParent();
    final ASTNode owner = parent.getNode();
    if (variables.size() == 1 && owner != null) {
      GroovyVariableUtil.cleanAroundDeclarationBeforeRemove(varDecl);
      owner.removeChild(varDeclNode);
      reformatCode(parent);
      return;
    }
    GroovyVariableUtil.cleanAroundVariableBeforeRemove(variable);
    final ASTNode varNode = variable.getNode();
    if (varNode != null) {
      varDeclNode.removeChild(varNode);
    }
    reformatCode(varDecl);
  }

  public static PsiElement realPrevious(PsiElement previousLeaf) {
    while (previousLeaf != null &&
        (previousLeaf instanceof PsiWhiteSpace ||
            previousLeaf instanceof PsiComment ||
            previousLeaf instanceof PsiErrorElement)) {
      previousLeaf = previousLeaf.getPrevSibling();
    }
    return previousLeaf;
  }

  public static PsiElement realNext(PsiElement nextLeaf) {
    while (nextLeaf != null &&
        (nextLeaf instanceof PsiWhiteSpace ||
            nextLeaf instanceof PsiComment ||
            nextLeaf instanceof PsiErrorElement)) {
      nextLeaf = nextLeaf.getNextSibling();
    }
    return nextLeaf;
  }

  public static void reformatCode(final PsiElement element) {
    final TextRange textRange = element.getTextRange();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          CodeStyleManager.getInstance(element.getProject()).reformatText(element.getContainingFile(),
              textRange.getStartOffset(), textRange.getEndOffset());
        } catch (IncorrectOperationException e) {
          e.printStackTrace();
        }
      }
    });
  }

  /**
   * Returns priiority level of expression
   *
   * @param expr
   * @return
   */
  public static int getExprPriorityLevel(GrExpression expr) {
    int priority = 0;
    //if (expr instanceof GrNewExpression) priority = 1;
    if (expr instanceof GrPostfixExpression) priority = 5;
    if (expr instanceof GrUnaryExpression ||
        expr instanceof GrTypeCastExpression) priority = 6;
    if (expr instanceof GrPowerExprImpl) priority = 7;
    if (expr instanceof GrMultiplicativeExprImpl) priority = 8;
    if (expr instanceof GrAdditiveExprImpl) priority = 9;
    if (expr instanceof GrShiftExprImpl) priority = 10;
    if (expr instanceof GrRelationalExpression) priority = 11;
    if (expr instanceof GrEqualityExprImpl) priority = 12;
    if (expr instanceof GrRegexExprImpl) priority = 13;
    if (expr instanceof GrAndExprImpl) priority = 14;
    if (expr instanceof GrExclusiveOrExprImpl) priority = 15;
    if (expr instanceof GrInclusiveOrExprImpl) priority = 16;
    if (expr instanceof GrLogicalAndExprImpl) priority = 17;
    if (expr instanceof GrLogicalOrExprImpl) priority = 18;
    if (expr instanceof GrConditionalExpression) priority = 19;
    if (expr instanceof GrAssignmentExpression) priority = 20;
    return -priority;
  }

  public static void setName(String name, PsiElement nameElement) {
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyElementFactory.getInstance(nameElement.getProject()).createReferenceNameFromText(name).getNode();
    assert newNameNode != null && node != null;
    node.getTreeParent().replaceChild(node, newNameNode);
  }

  public static boolean isExtendsSignature(MethodSignature superSignatureCandidate, MethodSignature subSignature) {
    final String name1 = superSignatureCandidate.getName();
    final String name2 = subSignature.getName();
    if (!name1.equals(name2)) return false;

    final PsiType[] superTypes = superSignatureCandidate.getParameterTypes();
    final PsiType[] subTypes = subSignature.getParameterTypes();
    if (subTypes.length != superTypes.length) return false;
    for (int i = 0; i < subTypes.length - 1; i++) {
      PsiType superType = TypeConversionUtil.erasure(superTypes[i]);
      PsiType subType = TypeConversionUtil.erasure(subTypes[i]);
      if (superType.isAssignableFrom(subType)) return false;
    }

    if (superTypes.length > 0) {
      final PsiType lastSuperType = TypeConversionUtil.erasure(superTypes[superTypes.length - 1]);
      final PsiType lastSubType = TypeConversionUtil.erasure(subTypes[superTypes.length - 1]);
      if (lastSuperType instanceof PsiArrayType && !(lastSubType instanceof PsiArrayType)) {
        final PsiType componentType = ((PsiArrayType) lastSuperType).getComponentType();
        if (!lastSubType.isConvertibleFrom(componentType)) return false;
      } else {
        if (!lastSuperType.isAssignableFrom(lastSubType)) return false;
      }
    }

    return true;
  }
}
