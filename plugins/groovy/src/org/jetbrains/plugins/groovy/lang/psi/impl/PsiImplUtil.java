/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrAndExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrExclusiveOrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrInclusiveOrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.GrLogicalAndExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.GrLogicalOrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex.GrRegexExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrEqualityExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.impl.source.tree.Factory.createSingleLeafElement;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

/**
 *
 */
public class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil");
  private static final String MAIN_METHOD = "main";

  private PsiImplUtil() {
  }

  public static GrExpression replaceExpression(GrExpression oldExpr, GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    PsiElement oldParent = oldExpr.getParent();
    if (oldParent == null) throw new PsiInvalidElementAccessException(oldExpr);

    ASTNode parentNode = oldParent.getNode();

    if (newExpr instanceof GrApplicationStatement && !(oldExpr instanceof GrApplicationStatement)) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(oldExpr.getProject());
      newExpr = factory.createMethodCallByAppCall(((GrApplicationStatement)newExpr));
    }

    // Remove unnecessary parentheses
    if (removeUnnecessaryParentheses && oldParent instanceof GrParenthesizedExpression) {
      return ((GrExpression)oldParent).replaceWithExpression(newExpr, removeUnnecessaryParentheses);
    }

    // check priorities
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(oldExpr.getProject());
    if (GrStringUtil.isReplacedExpressionInGStringInjection(oldExpr)) {
      /*if (newExpr instanceof GrLiteral) {            todo Max Medvedev
        return GrStringUtil.replaceStringInjectionByLiteral(oldExpr, ((GrLiteral)newExpr));
      }
      else */if (!(newExpr instanceof GrReferenceExpression)){
        newExpr = factory.createExpressionFromText("{" + newExpr.getText() + "}");
      }
    }
    else if (oldParent instanceof GrExpression && !(oldParent instanceof GrParenthesizedExpression)) {
      GrExpression result = addParenthesesIfNeeded(newExpr, oldExpr, (GrExpression)oldParent);
      if (result != null) return result;
    }

    ASTNode oldNode = oldExpr.getNode();
    ASTNode newNode = newExpr.copy().getNode();
    assert newNode != null && parentNode != null;
    parentNode.replaceChild(oldNode, newNode);

    return ((GrExpression)newNode.getPsi());
  }

  /**
   * @return replaced exprssion or null if expression is not replaced
   */
  @Nullable
  private static GrExpression addParenthesesIfNeeded(GrExpression newExpr, GrExpression oldExpr, GrExpression oldParent) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(oldExpr.getProject());

    int parentPriorityLevel = getExprPriorityLevel(oldParent);
    int newPriorityLevel = getExprPriorityLevel(newExpr);

    boolean isReplaced = false;

    if (parentPriorityLevel > newPriorityLevel) {
      newExpr = factory.createParenthesizedExpr(newExpr);
      isReplaced = true;
    }
    else if (parentPriorityLevel == newPriorityLevel && parentPriorityLevel != 0) {
      if (oldParent instanceof GrBinaryExpression) {
        GrBinaryExpression binaryExpression = (GrBinaryExpression)oldParent;
        if (isNotAssociative(binaryExpression) && oldExpr.equals(binaryExpression.getRightOperand())) {
          newExpr = factory.createParenthesizedExpr(newExpr);
          isReplaced = true;
        }
      }
    }
    if (isReplaced) {
      newExpr = (GrExpression)oldExpr.replace(newExpr);
      final GrCommandArgumentList commandArgList =
        PsiTreeUtil.getParentOfType(oldParent, GrCommandArgumentList.class, true, GrCodeBlock.class, GrParenthesizedExpression.class);
      if (commandArgList == null) return newExpr;
      final PsiElement[] args = commandArgList.getAllArguments();

      if (PsiTreeUtil.isAncestor(args[0], newExpr, true)) {
        final PsiElement parent = commandArgList.getParent();
        LOG.assertTrue(parent instanceof GrApplicationStatement);

        return (GrExpression)parent.replace(factory.createExpressionFromText(
          ((GrApplicationStatement)parent).getInvokedExpression().getText() + "(" + commandArgList.getText() + ")"));

      } else {
        return newExpr;
      }
    }
    return null;
  }

  private static boolean isNotAssociative(GrBinaryExpression binaryExpression) {
    if (binaryExpression instanceof GrMultiplicativeExpressionImpl) {
      return binaryExpression.getOperationTokenType() != mSTAR;
    }
    if (binaryExpression instanceof GrAdditiveExpressionImpl) {
      return binaryExpression.getOperationTokenType() == mMINUS;
    }
    return binaryExpression instanceof GrEqualityExpressionImpl
        || binaryExpression instanceof GrRegexExpressionImpl
        || binaryExpression instanceof GrShiftExpressionImpl
        || binaryExpression instanceof GrPowerExpressionImpl;
  }

  @Nullable
  public static GrExpression getRuntimeQualifier(GrReferenceExpression refExpr) {
    GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      GrClosableBlock closure = PsiTreeUtil.getParentOfType(refExpr, GrClosableBlock.class);
      while (closure != null) {
        PsiElement parent = closure.getParent();
        if (parent instanceof GrArgumentList) parent = parent.getParent();
        if (parent instanceof GrMethodCall) {
          GrExpression funExpr = ((GrMethodCall)parent).getInvokedExpression();
          if (funExpr instanceof GrReferenceExpression && ((GrReferenceExpression)funExpr).resolve() instanceof PsiMethod) {
            qualifier = ((GrReferenceExpression) funExpr).getQualifierExpression();
            if (qualifier != null) {
              return qualifier;
            }
          }
          else {
            return funExpr;
          }
        }

        closure = PsiTreeUtil.getParentOfType(closure, GrClosableBlock.class);
      }
    }

    return qualifier;
  }

  public static void removeVariable(GrVariable variable) {
    final GrVariableDeclaration varDecl = (GrVariableDeclaration) variable.getParent();
    final List<GrVariable> variables = Arrays.asList(varDecl.getVariables());
    if (!variables.contains(variable)) {
      throw new IllegalArgumentException();
    }

    final PsiElement parent = varDecl.getParent();
    final ASTNode owner = parent.getNode();
    if (variables.size() == 1 && owner != null) {
      PsiElement next = varDecl.getNextSibling();

      // remove redundant semicolons
      //noinspection ConstantConditions
      while (next != null && next.getNode() != null && next.getNode().getElementType() == mSEMI) {
        PsiElement tmpNext = next.getNextSibling();
        //noinspection ConstantConditions
        next.delete();
        next = tmpNext;
      }

      removeNewLineAfter(varDecl);
      varDecl.delete();
      return;
    }
    variable.delete();
  }

  @Nullable
  public static PsiElement realPrevious(PsiElement previousLeaf) {
    while (previousLeaf != null &&
        (previousLeaf instanceof PsiWhiteSpace ||
            previousLeaf instanceof PsiComment ||
            previousLeaf instanceof PsiErrorElement)) {
      previousLeaf = previousLeaf.getPrevSibling();
    }
    return previousLeaf;
  }

  private static int getExprPriorityLevel(GrExpression expr) {
    int priority = 0;
    //if (expr instanceof GrNewExpression) priority = 1;
    if (expr instanceof GrPostfixExpression) priority = 5;
    if (expr instanceof GrUnaryExpression ||
        expr instanceof GrTypeCastExpression) priority = 6;
    if (expr instanceof GrPowerExpressionImpl) priority = 7;
    if (expr instanceof GrMultiplicativeExpressionImpl) priority = 8;
    if (expr instanceof GrAdditiveExpressionImpl) priority = 9;
    if (expr instanceof GrShiftExpressionImpl) priority = 10;
    if (expr instanceof GrRangeExpressionImpl) priority = 11;
    if (expr instanceof GrRelationalExpression) priority = 12;
    if (expr instanceof GrEqualityExpressionImpl) priority = 13;
    if (expr instanceof GrRegexExpressionImpl) priority = 14;
    if (expr instanceof GrAndExpressionImpl) priority = 15;
    if (expr instanceof GrExclusiveOrExpressionImpl) priority = 16;
    if (expr instanceof GrInclusiveOrExpressionImpl) priority = 17;
    if (expr instanceof GrLogicalAndExpressionImpl) priority = 18;
    if (expr instanceof GrLogicalOrExpressionImpl) priority = 19;
    if (expr instanceof GrConditionalExpression) priority = 20;
    if (expr instanceof GrSafeCastExpression) priority = 21;
    if (expr instanceof GrAssignmentExpression) priority = 22;
    if (expr instanceof GrApplicationStatement) priority = 23;
    return -priority;
  }

  public static void setName(String name, PsiElement nameElement) {
    final PsiElement newNameElement = GroovyPsiElementFactory.getInstance(nameElement.getProject()).createReferenceNameFromText(name);
    nameElement.replace(newNameElement);
  }

  public static boolean isExtendsSignature(MethodSignature superSignatureCandidate, MethodSignature subSignature) {
    return MethodSignatureUtil.isSubsignature(superSignatureCandidate, subSignature);
  }

  @Nullable
  public static PsiElement getOriginalElement(PsiClass clazz, PsiFile containingFile) {
    VirtualFile vFile = containingFile.getVirtualFile();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(clazz.getProject());
    final ProjectFileIndex idx = ProjectRootManager.getInstance(facade.getProject()).getFileIndex();

    if (vFile == null || !idx.isInLibrarySource(vFile)) return clazz;
    final String qName = clazz.getQualifiedName();
    if (qName == null) return null;
    final List<OrderEntry> orderEntries = idx.getOrderEntriesForFile(vFile);
    PsiClass original = facade.findClass(qName, new GlobalSearchScope(facade.getProject()) {
      public int compare(VirtualFile file1, VirtualFile file2) {
        return 0;
      }

      public boolean contains(VirtualFile file) {
        // order for file and vFile has non empty intersection.
        List<OrderEntry> entries = idx.getOrderEntriesForFile(file);
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < entries.size(); i++) {
          final OrderEntry entry = entries.get(i);
          if (orderEntries.contains(entry)) return true;
        }
        return false;
      }

      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return false;
      }

      public boolean isSearchInLibraries() {
        return true;
      }
    });

    return original != null ? original : clazz;
  }

  @Nullable
  public static PsiMethod extractUniqueElement(@NotNull GroovyResolveResult[] results) {
    if (results.length != 1) return null;
    final PsiElement element = results[0].getElement();
    return element instanceof PsiMethod ? (PsiMethod) element : null;
  }

  @NotNull
  public static GroovyResolveResult extractUniqueResult(@NotNull GroovyResolveResult[] results) {
    if (results.length != 1) return GroovyResolveResult.EMPTY_RESULT;
    return results[0];
  }

  public static PsiMethod[] mapToMethods(@Nullable List<CandidateInfo> list) {
    if (list == null) return PsiMethod.EMPTY_ARRAY;
    PsiMethod[] result = new PsiMethod[list.size()];
    for (int i = 0; i < list.size(); i++) {
      result[i] = (PsiMethod) list.get(i).getElement();

    }
    return result;
  }

  public static String getName(GrNamedElement namedElement) {
    PsiElement nameElement = namedElement.getNameIdentifierGroovy();
    ASTNode node = nameElement.getNode();
    LOG.assertTrue(node != null);
    if (node.getElementType() == mIDENT) return nameElement.getText();
    else {
      if (node.getElementType() == mSTRING_LITERAL) {
        String text = nameElement.getText();
        return text.endsWith("'") ? text.substring(1, text.length() - 1) : text.substring(1);
      } else {
        LOG.assertTrue(node.getElementType() == mGSTRING_LITERAL);
        String text = nameElement.getText();
        return text.endsWith("\"") ? text.substring(1, text.length() - 1) : text.substring(1);
      }
    }
  }

  public static void removeNewLineAfter(@NotNull GrStatement statement) {
    ASTNode parentNode = statement.getParent().getNode();
    ASTNode next = statement.getNode().getTreeNext();
    if (parentNode != null && next != null && mNLS == next.getElementType()) {
      parentNode.removeChild(next);
    }
  }

  public static boolean isMainMethod(GrMethod method) {
    return method.getName().equals(MAIN_METHOD) &&
        method.hasModifierProperty(PsiModifier.STATIC);
  }

  public static PsiType getNominalType(final GrExpression expr) {
    final TypeInferenceHelper helper = GroovyPsiManager.getInstance(expr.getProject()).getTypeInferenceHelper();

    return helper.doWithInferenceDisabled(new Computable<PsiType>() {
      public PsiType compute() {
        return expr.getType();
      }
    });
  }

  public static void deleteStatementTail(PsiElement container, @NotNull PsiElement statement) {
    PsiElement next = statement.getNextSibling();
    while (next != null) {
      final ASTNode node = next.getNode();
      final IElementType type = node.getElementType();
      if (type == mSEMI) {
        final PsiElement nnext = next.getNextSibling();
        container.deleteChildRange(next, next);
        next = nnext;
      }
      else if (type == mNLS || type == TokenType.WHITE_SPACE && next.getText().contains("\n")) {
        final String text = next.getText();
        final int first = text.indexOf("\n");
        final int second = text.indexOf("\n", first + 1);
        if (second < 0) {
          container.deleteChildRange(next, next);
          return;
        }
        final String substring = text.substring(second);
        container.getNode()
          .replaceChild(node, createSingleLeafElement(type, substring, 0, substring.length(), null, container.getManager()));
        return;
      }
      else {
        break;
      }
    }
  }

  public static <T extends PsiElement> void setQualifier(GrQualifiedReference<T> ref, T newQualifier) {
    final T oldQualifier = ref.getQualifier();
    final ASTNode node = ref.getNode();
    final PsiElement refNameElement = ref.getReferenceNameElement();
    if (newQualifier == null) {
      if (oldQualifier != null && refNameElement != null) {
        ref.deleteChildRange(ref.getFirstChild(), refNameElement.getPrevSibling());
      }
    } else {
      if (oldQualifier == null) {
        if (refNameElement != null) {
          ref.addBefore(newQualifier, refNameElement);
          node.addLeaf(GroovyTokenTypes.mDOT, ".", refNameElement.getNode());
        }
      }
      else {
        oldQualifier.replace(newQualifier);
      }
    }
  }

}
