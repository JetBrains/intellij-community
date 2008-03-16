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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrAndExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrExclusiveOrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.bitwise.GrInclusiveOrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.GrLogicalAndExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.logical.GrLogicalOrExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.regex.GrRegexExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.relational.GrEqualityExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil");

  public static GrExpression replaceExpression(GrExpression oldExpr,
                                               GrExpression newExpr,
                                               boolean removeUnnecessaryParentheses) {
    ASTNode oldNode = oldExpr.getNode();
    PsiElement oldParent = oldExpr.getParent();
    if (oldParent == null) throw new PsiInvalidElementAccessException(oldExpr);

    ASTNode parentNode = oldParent.getNode();

    if (newExpr instanceof GrApplicationStatement && !(oldExpr instanceof GrApplicationStatement)) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(oldExpr.getProject());
      newExpr = factory.createMethodCallByAppCall(((GrApplicationStatement) newExpr));
    }

    // Remove unnecessary parentheses
    if (removeUnnecessaryParentheses && oldParent instanceof GrParenthesizedExpression) {
      return ((GrExpression) oldParent).replaceWithExpression(newExpr, removeUnnecessaryParentheses);
    }

    // check priorities
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(oldExpr.getProject());
    if (oldParent instanceof GrExpression && !(oldParent instanceof GrParenthesizedExpression)) {
      GrExpression parentExpr = (GrExpression) oldParent;
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

    ASTNode newNode = newExpr.copy().getNode();
    assert newNode != null && parentNode != null;
    parentNode.replaceChild(oldNode, newNode);

    return ((GrExpression) newNode.getPsi());
  }

  private static boolean isNotAssociative(GrBinaryExpression binaryExpression) {
    if (binaryExpression instanceof GrMultiplicativeExpressionImpl) {
      return binaryExpression.getOperationTokenType() != GroovyTokenTypes.mSTAR;
    }
    if (binaryExpression instanceof GrAdditiveExpressionImpl) {
      return binaryExpression.getOperationTokenType() == GroovyTokenTypes.mMINUS;
    }
    return binaryExpression instanceof GrEqualityExpressionImpl
        || binaryExpression instanceof GrRegexExpressionImpl
        || binaryExpression instanceof GrShiftExpressionImpl
        || binaryExpression instanceof GrPowerExpressionImpl;
  }

  public static SearchScope getUseScope(GrMember member) {
    if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
      return new LocalSearchScope(member.getContainingFile()); //todo: what does GLS say?
    }

    return member.getManager().getSearchHelper().getUseScope(member);
  }

  public static PsiNamedElement[] getMethodVariants(GrReferenceExpression methodReference) {
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

  public static void removeVariable(GrVariable variable) {
    final GrVariableDeclaration varDecl = (GrVariableDeclaration) variable.getParent();
    final List<GrVariable> variables = Arrays.asList(varDecl.getVariables());
    if (!variables.contains(variable)) {
      throw new IllegalArgumentException();
    }

    final ASTNode varDeclNode = varDecl.getNode();
    final PsiElement parent = varDecl.getParent();
    final ASTNode owner = parent.getNode();
    if (variables.size() == 1 && owner != null) {
      owner.removeChild(varDeclNode);
      PsiUtil.reformatCode(parent);
      return;
    }
    final ASTNode varNode = variable.getNode();
    if (varNode != null) {
      varDeclNode.removeChild(varNode);
    }
    PsiUtil.reformatCode(varDecl);
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
    if (expr instanceof GrAssignmentExpression) priority = 21;
    if (expr instanceof GrApplicationStatement) priority = 22;
    return -priority;
  }

  public static void setName(String name, PsiElement nameElement) {
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(nameElement.getProject()).createReferenceNameFromText(name).getNode();
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
      if (!superType.isAssignableFrom(subType)) return false;
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

  public static PsiElement getOriginalElement(PsiClass clazz, PsiFile containingFile) {
    VirtualFile vFile = containingFile.getVirtualFile();
    final PsiManager manager = clazz.getManager();
    final ProjectFileIndex idx = ProjectRootManager.getInstance(manager.getProject()).getFileIndex();

    if (vFile == null || !idx.isInLibrarySource(vFile)) return clazz;
    final String qName = clazz.getQualifiedName();
    if (qName == null) return null;
    final List<OrderEntry> orderEntries = idx.getOrderEntriesForFile(vFile);
    PsiClass original = manager.findClass(qName, new GlobalSearchScope() {
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

  public static PsiMethod extractUniqueElement(GroovyResolveResult[] results) {
    if (results.length != 1) return null;
    final PsiElement element = results[0].getElement();
    return element instanceof PsiMethod ? (PsiMethod) element : null;
  }

  public static GroovyResolveResult extractUniqueResult(GroovyResolveResult[] results) {
    if (results.length != 1) return null;
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
    if (node.getElementType() == GroovyTokenTypes.mIDENT) return nameElement.getText();
    else {
      if (node.getElementType() == GroovyTokenTypes.mSTRING_LITERAL) {
        String text = nameElement.getText();
        return text.endsWith("'") ? text.substring(1, text.length() - 1) : text.substring(1);
      } else {
        LOG.assertTrue(node.getElementType() == GroovyTokenTypes.mGSTRING_LITERAL);
        String text = nameElement.getText();
        return text.endsWith("\"") ? text.substring(1, text.length() - 1) : text.substring(1);
      }
    }
  }

  public static void removeNewLineAfter(@NotNull GrStatement statement) {
    ASTNode parentNode = statement.getParent().getNode();
    ASTNode next = statement.getNode().getTreeNext();
    if (parentNode != null && next != null && GroovyTokenTypes.mNLS == next.getElementType()) {
      parentNode.removeChild(next);
    }
  }
}
