/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.AstBufferUtil;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.GeeseUtil;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrAdditiveExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrMultiplicativeExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.GrRangeExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrSyntheticCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrSyntheticExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrSyntheticTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Arrays;
import java.util.List;

import static com.intellij.psi.impl.source.tree.Factory.createSingleLeafElement;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.RELATIONS;
import static org.jetbrains.plugins.groovy.lang.lexer.TokenSets.SHIFT_SIGNS;

public class PsiImplUtil {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil");
  private static final String MAIN_METHOD = "main";
  public static final Key<SoftReference<PsiCodeBlock>> PSI_CODE_BLOCK = Key.create("Psi_code_block");
  public static final Key<SoftReference<PsiTypeElement>> PSI_TYPE_ELEMENT = Key.create("psi.type.element");
  public static final Key<SoftReference<PsiExpression>> PSI_EXPRESSION = Key.create("psi.expression");

  private PsiImplUtil() {
  }

  /**
   * @return leading regex or null if it does not exist
   */
  @Nullable
  private static GrLiteral getRegexAtTheBeginning(PsiElement expr) {
    PsiElement fchild = expr;
    while (fchild != null) {
      if (fchild instanceof GrLiteral && GrStringUtil.isRegex((GrLiteral)fchild)) return (GrLiteral)fchild;
      fchild = fchild.getFirstChild();
    }
    return null;
  }

  private static boolean isAfterIdentifier(PsiElement el) {
    final PsiElement prev = GeeseUtil.getPreviousNonWhitespaceToken(el);
    return prev != null && prev.getNode().getElementType() == mIDENT;
  }

  @Nullable
  public static GrExpression replaceExpression(GrExpression oldExpr, GrExpression newExpr, boolean removeUnnecessaryParentheses) {
    PsiElement oldParent = oldExpr.getParent();
    if (oldParent == null) throw new PsiInvalidElementAccessException(oldExpr);

    if (!(oldExpr instanceof GrApplicationStatement)) {
      newExpr = ApplicationStatementUtil.convertToMethodCallExpression(newExpr);
    }

    // Remove unnecessary parentheses
    if (removeUnnecessaryParentheses &&
        oldParent instanceof GrParenthesizedExpression &&
        !(oldParent.getParent() instanceof GrArgumentLabel)) {
      return ((GrExpression)oldParent).replaceWithExpression(newExpr, removeUnnecessaryParentheses);
    }

    //regexes cannot be after identifier , try to replace it with simple string
    if (getRegexAtTheBeginning(newExpr) != null && isAfterIdentifier(oldExpr)) {
      final PsiElement copy = newExpr.copy();
      final GrLiteral regex = getRegexAtTheBeginning(copy);
      LOG.assertTrue(regex != null);
      final GrLiteral stringLiteral = GrStringUtil.createStringFromRegex(regex);
      if (regex == copy) {
        return oldExpr.replaceWithExpression(stringLiteral, removeUnnecessaryParentheses);
      }
      else {
        regex.replace(stringLiteral);
        return oldExpr.replaceWithExpression((GrExpression)copy, removeUnnecessaryParentheses);
      }
    }

    
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(oldExpr.getProject());
    if (oldParent instanceof GrStringInjection) {
      if (newExpr instanceof GrString || newExpr instanceof GrLiteral && ((GrLiteral)newExpr).getValue() instanceof String) {
        return GrStringUtil.replaceStringInjectionByLiteral((GrStringInjection)oldParent, (GrLiteral)newExpr);
      }
      else {
        newExpr = factory.createExpressionFromText("{" + newExpr.getText() + "}");
        oldParent.getNode().replaceChild(oldExpr.getNode(), newExpr.getNode());
        return newExpr;
      }
    }
    
    if (PsiTreeUtil.getParentOfType(oldExpr, GrStringInjection.class, false, GrCodeBlock.class) != null) {
      final PsiElement replaced = oldExpr.replace(newExpr);
      final GrStringInjection stringInjection = PsiTreeUtil.getParentOfType(replaced, GrStringInjection.class);
      GrStringUtil.wrapInjection(stringInjection);
      assert stringInjection != null;
      return stringInjection.getClosableBlock();
    }
    
    //check priorities    
    if (oldParent instanceof GrExpression && !(oldParent instanceof GrParenthesizedExpression)) {
      GrExpression addedParenth = addParenthesesIfNeeded(newExpr, oldExpr, (GrExpression)oldParent);
      if (newExpr != addedParenth) {
        return oldExpr.replaceWithExpression(addedParenth, removeUnnecessaryParentheses);
      }
    }

    //if replace closure argument with expression
    //we should add the expression in arg list
    if (oldExpr instanceof GrClosableBlock &&
        !(newExpr instanceof GrClosableBlock) &&
        oldParent instanceof GrMethodCallExpression &&
        ArrayUtil.contains(oldExpr, ((GrMethodCallExpression)oldParent).getClosureArguments())) {
      return ((GrMethodCallExpression)oldParent).replaceClosureArgument((GrClosableBlock)oldExpr, newExpr);

    }

    newExpr = (GrExpression)oldExpr.replace(newExpr);

    if (newExpr instanceof GrParenthesizedExpression) {
      final GrCommandArgumentList commandArgList =
        PsiTreeUtil.getParentOfType(oldParent, GrCommandArgumentList.class, true, GrCodeBlock.class, GrParenthesizedExpression.class);
      if (commandArgList != null) {
        final PsiElement[] args = commandArgList.getAllArguments();
        if (PsiTreeUtil.isAncestor(args[0], newExpr, true)) {
          final PsiElement parent = commandArgList.getParent();
          LOG.assertTrue(parent instanceof GrApplicationStatement);

          GrExpression invokedExpression = ((GrApplicationStatement)parent).getInvokedExpression();
          assert invokedExpression != null;
          return (GrExpression)parent.replace(factory.createExpressionFromText(invokedExpression.getText() + "(" + commandArgList.getText() + ")"));
        }
      }
    }
    return newExpr;
  }

  /**
   * @return replaced expression or null if expression is not replaced
   */
  @Nullable
  private static GrExpression addParenthesesIfNeeded(GrExpression newExpr, GrExpression oldExpr, GrExpression oldParent) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(oldExpr.getProject());

    int parentPriorityLevel = getExprPriorityLevel(oldParent);
    int newPriorityLevel = getExprPriorityLevel(newExpr);

    if (parentPriorityLevel > newPriorityLevel) {
      newExpr = factory.createParenthesizedExpr(newExpr);
    }
    else if (parentPriorityLevel == newPriorityLevel && parentPriorityLevel != 0) {
      if (oldParent instanceof GrBinaryExpression) {
        GrBinaryExpression binaryExpression = (GrBinaryExpression)oldParent;
        if (isNotAssociative(binaryExpression) && oldExpr.equals(binaryExpression.getRightOperand())) {
          newExpr = factory.createParenthesizedExpr(newExpr);
        }
      }
    }
    return newExpr;
  }

  private static boolean isNotAssociative(GrBinaryExpression binaryExpression) {
    final IElementType opToken = binaryExpression.getOperationTokenType();
    if (binaryExpression instanceof GrMultiplicativeExpressionImpl) {
      return opToken != mSTAR;
    }
    if (binaryExpression instanceof GrAdditiveExpressionImpl) {
      return opToken == mMINUS;
    }
    return RELATIONS.contains(opToken) || opToken == mCOMPARE_TO
           || opToken == mREGEX_FIND || opToken == mREGEX_MATCH
           || SHIFT_SIGNS.contains(opToken)
           || opToken==mSTAR;
  }

  @Nullable
  public static GrExpression getRuntimeQualifier(GrReferenceExpression refExpr) {
    GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier != null) return qualifier;

    for (GrClosableBlock closure = PsiTreeUtil.getParentOfType(refExpr, GrClosableBlock.class);
         closure != null;
         closure = PsiTreeUtil.getParentOfType(closure, GrClosableBlock.class)) {

      PsiElement parent = closure.getParent();
      if (parent instanceof GrArgumentList) parent = parent.getParent();
      if (!(parent instanceof GrMethodCall)) continue;

      GrExpression funExpr = ((GrMethodCall)parent).getInvokedExpression();
      if (!(funExpr instanceof GrReferenceExpression)) return funExpr;

      final PsiElement resolved = ((GrReferenceExpression)funExpr).resolve();
      if (!(resolved instanceof PsiMethod)) return funExpr;

      if (resolved instanceof GrGdkMethod &&
          isFromDGM((GrGdkMethod)resolved) &&
          !GdkMethodUtil.isWithName(((GrGdkMethod)resolved).getStaticMethod().getName())) {
        continue;
      }

      qualifier = ((GrReferenceExpression)funExpr).getQualifierExpression();
      if (qualifier != null) return qualifier;
    }

    return null;
  }

  private static boolean isFromDGM(GrGdkMethod resolved) {
    final PsiClass containingClass = resolved.getStaticMethod().getContainingClass();
    return containingClass != null && GroovyCommonClassNames.DEFAULT_GROOVY_METHODS.equals(containingClass.getQualifiedName());
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
    if (expr instanceof GrUnaryExpression) priority = ((GrUnaryExpression)expr).isPostfix() ? 5 : 6;
    else if (expr instanceof GrTypeCastExpression) priority = 6;

    else if (expr instanceof GrRangeExpressionImpl) priority = 11;

    else if (expr instanceof GrBinaryExpression) {
      final IElementType opToken = ((GrBinaryExpression)expr).getOperationTokenType();

      if (opToken == mSTAR_STAR) priority = 7;
      else if (opToken == mSTAR || opToken == mDIV) priority = 8;
      else if (opToken == mPLUS || opToken == mMINUS) priority = 9;
      else if (SHIFT_SIGNS.contains(opToken)) priority = 10;
      else if (RELATIONS.contains(opToken)) priority = 12;
      else if (opToken == mEQUAL || opToken == mNOT_EQUAL || opToken == mCOMPARE_TO) priority = 13;
      else if (opToken == mREGEX_FIND || opToken == mREGEX_MATCH) priority = 14;
      else if (opToken == mBAND) priority = 15;
      else if (opToken == mBXOR) priority = 16;
      else if (opToken == mBOR) priority = 17;
      else if (opToken == mLAND) priority = 18;
      else if (opToken == mLOR) priority = 19;
    }
    else if (expr instanceof GrConditionalExpression) priority = 20;
    else if (expr instanceof GrSafeCastExpression) priority = 21;
    else if (expr instanceof GrAssignmentExpression) priority = 22;
    else if (expr instanceof GrApplicationStatement) priority = 23;

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
    if (!method.getName().equals(MAIN_METHOD)) return false;
    else if (!method.hasModifierProperty(PsiModifier.STATIC))return false;

    final GrParameter[] parameters = method.getParameters();

    if (parameters.length == 0) return false;
    if (parameters.length == 1 && parameters[0].getTypeElementGroovy() == null) return true;

    int args_count = 0;
    int optional_count = 0;

    for (GrParameter p : parameters) {
      final GrTypeElement declaredType = p.getTypeElementGroovy();
      if ((declaredType == null || declaredType.getType().equalsToText(CommonClassNames.JAVA_LANG_STRING + "[]")) &&
          p.getDefaultInitializer() == null) {
        args_count++;
      }
      if (p.getDefaultInitializer() != null) optional_count++;
    }

    return optional_count == parameters.length - 1 && args_count == 1;
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
          node.addLeaf(mDOT, ".", refNameElement.getNode());
          ref.addBefore(newQualifier, refNameElement.getPrevSibling());
        }
      }
      else {
        oldQualifier.replace(newQualifier);
      }
    }
  }

  public static boolean isSimpleArrayAccess(PsiType exprType, PsiType[] argTypes, PsiManager manager, GlobalSearchScope resolveScope, boolean isLValue) {
    return exprType instanceof PsiArrayType &&
           (isLValue && argTypes.length == 2 || !isLValue && argTypes.length == 1) &&
           TypesUtil.isAssignable(PsiType.INT, argTypes[0], manager, resolveScope);
  }

  public static String getTextSkipWhiteSpaceAndComments(ASTNode node) {
    return AstBufferUtil.getTextSkippingWhitespaceComments(node);
  }

  @Nullable
  public static PsiCodeBlock getOrCreatePsiCodeBlock(GrOpenBlock block) {
    if (block == null) return null;

    final SoftReference<PsiCodeBlock> ref = block.getUserData(PSI_CODE_BLOCK);
    final PsiCodeBlock body = ref == null ? null : ref.get();
    if (body != null) return body;
    final GrSyntheticCodeBlock newBody = new GrSyntheticCodeBlock(block);
    block.putUserData(PSI_CODE_BLOCK, new SoftReference<PsiCodeBlock>(newBody));
    return newBody;
  }

  @Nullable
  public static PsiTypeElement getOrCreateTypeElement(@Nullable GrTypeElement typeElement) {
    if (typeElement == null) return null;

    final SoftReference<PsiTypeElement> ref = typeElement.getUserData(PSI_TYPE_ELEMENT);
    final PsiTypeElement element = ref == null ? null : ref.get();
    if (element != null) return element;
    final GrSyntheticTypeElement newTypeElement = new GrSyntheticTypeElement(typeElement);
    typeElement.putUserData(PSI_TYPE_ELEMENT, new SoftReference<PsiTypeElement>(newTypeElement));
    return newTypeElement;
  }

  @Nullable
  public static PsiExpression getOrCreatePisExpression(@Nullable GrExpression expr) {
    if (expr == null) return null;

    final SoftReference<PsiExpression> ref = expr.getUserData(PSI_EXPRESSION);
    final PsiExpression element = ref == null ? null : ref.get();
    if (element != null) return element;
    final GrSyntheticExpression newExpr = new GrSyntheticExpression(expr);
    expr.putUserData(PSI_EXPRESSION, new SoftReference<PsiExpression>(newExpr));
    return newExpr;
  }


  public static <T extends GrCondition> T replaceBody(T newBody, GrStatement body, ASTNode node, Project project) {
    if (body == null || newBody == null) {
      throw new IncorrectOperationException();
    }
    ASTNode oldBodyNode = body.getNode();
    if (oldBodyNode.getTreePrev() != null && mNLS.equals(oldBodyNode.getTreePrev().getElementType())) {
      ASTNode whiteNode = GroovyPsiElementFactory.getInstance(project).createWhiteSpace().getNode();
      node.replaceChild(oldBodyNode.getTreePrev(), whiteNode);
    }
    node.replaceChild(oldBodyNode, newBody.getNode());
    return newBody;
  }

  public static boolean isVarArgs(GrParameter[] parameters) {
    return parameters.length > 0 && parameters[parameters.length - 1].isVarArgs();
  }

  @Nullable
  public static PsiAnnotation getAnnotation(@NotNull PsiModifierListOwner field, @NotNull String annotationName) {
    final PsiModifierList modifierList = field.getModifierList();
    if (modifierList == null) return null;
    return modifierList.findAnnotation(annotationName);
  }

  @Nullable
  public static GrConstructorInvocation getChainingConstructorInvocation(GrMethod constructor) {
    if (constructor instanceof GrReflectedMethod && ((GrReflectedMethod)constructor).getSkippedParameters().length > 0) return null;

    LOG.assertTrue(constructor.isConstructor());

    GrOpenBlock body = constructor.getBlock();
    if (body == null) return null;

    GrStatement[] statements = body.getStatements();

    if (statements.length > 0 && statements[0] instanceof GrConstructorInvocation) {
      return (GrConstructorInvocation) statements[0];
    }

    return null;
  }

  public static GrMethod[] getMethodOrReflectedMethods(GrMethod method) {
    final GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();
    return reflectedMethods.length > 0 ? reflectedMethods : new GrMethod[]{method};
  }

  @Nullable
  public static PsiType inferExpectedTypeForDiamond(GrExpression diamondNew) {
    PsiElement skipped = PsiUtil.skipParentheses(diamondNew, true);
    assert skipped != null;
    PsiElement pparent = skipped.getParent();
    if (pparent instanceof GrAssignmentExpression &&
        PsiTreeUtil.isAncestor(((GrAssignmentExpression)pparent).getRValue(), diamondNew, false)) {
      GrExpression lValue = ((GrAssignmentExpression)pparent).getLValue();
      if (PsiUtil.mightBeLValue(lValue)) {
        return lValue.getNominalType();
      }
    }
    else if (pparent instanceof GrVariable && ((GrVariable)pparent).getInitializerGroovy() == diamondNew) {
      return ((GrVariable)pparent).getDeclaredType();
    }
    else if (pparent instanceof GrListOrMap) {
      PsiElement ppparent = PsiUtil.skipParentheses(pparent.getParent(), true);

      if (ppparent instanceof GrAssignmentExpression &&
          PsiTreeUtil.isAncestor(((GrAssignmentExpression)ppparent).getRValue(), pparent, false)) {

        PsiElement lValue = PsiUtil.skipParentheses(((GrAssignmentExpression)ppparent).getLValue(), false);
        if (lValue instanceof GrTupleExpression) {
          GrExpression[] initializers = ((GrListOrMap)pparent).getInitializers();
          int index = ArrayUtil.find(initializers, diamondNew);
          GrExpression[] expressions = ((GrTupleExpression)lValue).getExpressions();
          if (index < expressions.length) {
            return expressions[index].getNominalType();
          }
        }
      }
    }

    return null;
  }

  @Nullable
  public static PsiType normalizeWildcardTypeByPosition(@NotNull PsiType type, @NotNull GrExpression expression) {
    GrExpression toplevel = expression;
    while (toplevel.getParent() instanceof GrIndexProperty &&
           ((GrIndexProperty)toplevel.getParent()).getInvokedExpression() == toplevel) {
      toplevel = (GrExpression)toplevel.getParent();
    }

    final PsiType normalized = doNormalizeWildcardByPosition(type, expression, toplevel);
    if (normalized instanceof PsiClassType && !PsiUtil.isAccessedForWriting(toplevel)) {
      return com.intellij.psi.util.PsiUtil.captureToplevelWildcards(normalized, expression);
    }

    return normalized;
  }

  @Nullable
  private static PsiType doNormalizeWildcardByPosition(final PsiType type, final GrExpression expression, final GrExpression toplevel) {
    if (type instanceof PsiCapturedWildcardType) {
      return doNormalizeWildcardByPosition(((PsiCapturedWildcardType)type).getWildcard(), expression, toplevel);
    }


    if (type instanceof PsiWildcardType) {
      final PsiWildcardType wildcardType = (PsiWildcardType)type;

      if (PsiUtil.isAccessedForWriting(toplevel)) {
        return wildcardType.isSuper() ? wildcardType.getBound() : PsiCapturedWildcardType.create(wildcardType, expression);
      }
      else {
        if (wildcardType.isExtends()) {
          return wildcardType.getBound();
        }
        else {
          return PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope());
        }
      }
    }
    else if (type instanceof PsiArrayType) {
      final PsiType componentType = ((PsiArrayType)type).getComponentType();
      final PsiType normalizedComponentType = doNormalizeWildcardByPosition(componentType, expression, toplevel);
      if (normalizedComponentType != componentType) {
        assert normalizedComponentType != null;
        return normalizedComponentType.createArrayType();
      }
    }

    return type;
  }

  public static boolean isWhiteSpace(@Nullable PsiElement element) {
    return hasElementType(element, TokenSets.WHITE_SPACES_SET);
  }

  public static boolean hasElementType(@Nullable PsiElement next, @NotNull final IElementType type) {
    if (next == null) return false;
    final ASTNode astNode = next.getNode();
    return astNode != null && astNode.getElementType() == type;
  }

  public static boolean hasElementType(@Nullable PsiElement next, final TokenSet set) {
    if (next == null) return false;
    final ASTNode astNode = next.getNode();
    return astNode != null && set.contains(astNode.getElementType());
  }
}
