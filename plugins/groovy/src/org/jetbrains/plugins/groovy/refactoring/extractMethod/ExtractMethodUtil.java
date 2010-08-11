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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.intentions.utils.DuplicatesUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrMemberOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;
import org.jetbrains.plugins.groovy.refactoring.inline.GroovyInlineMethodUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author ilyas
 */
public class ExtractMethodUtil {

  static PsiElement calculateAnchorToInsertBefore(GrMemberOwner owner, PsiElement startElement) {
    while (startElement != null && !isEnclosingDefinition(owner, startElement)) {
      if (startElement.getParent() instanceof GroovyFile) {
        return startElement.getNextSibling();
      }
      startElement = startElement.getParent();
      PsiElement parent = startElement.getParent();
      if (parent instanceof GroovyFile &&
          ((GroovyFile) parent).getScriptClass() == owner) {
        return startElement.getNextSibling();
      }
    }
    return startElement == null ? null : startElement.getNextSibling();
  }

  private static boolean isEnclosingDefinition(GrMemberOwner owner, PsiElement startElement) {
    if (owner instanceof GrTypeDefinition) {
      GrTypeDefinition definition = (GrTypeDefinition) owner;
      return startElement.getParent() == definition.getBody();
    }
    return false;
  }

  public enum MethodAccessQualifier {
    PUBLIC, PRIVATE, PROTECTED
  }

  @NotNull
  static GrStatement createResultStatement(ExtractMethodInfoHelper helper, @NotNull String methodName) {
    String name = helper.getOutputName();
    PsiType type = helper.getOutputType();
    GrStatement[] statements = helper.getStatements();
    GrMethodCallExpression callExpression = createMethodCallByHelper(methodName, helper);
    if ((name == null || PsiType.VOID.equals(type)) && !helper.isReturnStatement()) return callExpression;
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    if (helper.isReturnStatement()) {
      return factory.createStatementFromText("return " + callExpression.getText());
    } else if (name != null && mustAddVariableDeclaration(statements, name)) {
      return factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, callExpression,
                                               type.equalsToText("java.lang.Object") ? null : type, name);
    } else {
      return factory.createExpressionFromText(name + "= " + callExpression.getText());
    }
  }

  static boolean validateMethod(GrMethod method, ExtractMethodInfoHelper helper) {
    ArrayList<String> conflicts = new ArrayList<String>();
    GrMemberOwner owner = helper.getOwner();
    PsiMethod[] methods = ArrayUtil.mergeArrays(owner.getAllMethods(), new PsiMethod[]{method}, PsiMethod.class);
    final Map<PsiMethod, List<PsiMethod>> map = DuplicatesUtil.factorDuplicates(methods, new TObjectHashingStrategy<PsiMethod>() {
      public int computeHashCode(PsiMethod method) {
        return method.getSignature(PsiSubstitutor.EMPTY).hashCode();
      }

      public boolean equals(PsiMethod method1, PsiMethod method2) {
        return method1.getSignature(PsiSubstitutor.EMPTY).equals(method2.getSignature(PsiSubstitutor.EMPTY));
      }
    });

    List<PsiMethod> list = map.get(method);
    if (list == null) return true;
    for (PsiMethod psiMethod : list) {
      if (psiMethod != method) {
        PsiClass containingClass = psiMethod.getContainingClass();
        if (containingClass == null) return true;
        String message = containingClass instanceof GroovyScriptClass ?
            GroovyRefactoringBundle.message("method.is.already.defined.in.script", GroovyRefactoringUtil.getMethodSignature(method),
                CommonRefactoringUtil.htmlEmphasize(containingClass.getQualifiedName())) :
            GroovyRefactoringBundle.message("method.is.already.defined.in.class", GroovyRefactoringUtil.getMethodSignature(method),
                CommonRefactoringUtil.htmlEmphasize(containingClass.getQualifiedName()));
        conflicts.add(message);
      }
    }

    return conflicts.size() <= 0 || reportConflicts(conflicts, helper.getProject());
  }

  static boolean reportConflicts(final ArrayList<String> conflicts, final Project project) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }


  static void removeOldStatements(GrStatementOwner owner, ExtractMethodInfoHelper helper) throws IncorrectOperationException {
    owner.removeElements(helper.getInnerElements());
  }

  /*
  To declare or not a variable to which method call result will be assigned.
   */
  private static boolean mustAddVariableDeclaration(@NotNull GrStatement[] statements, @NotNull String varName) {
    for (GrStatement statement : statements) {
      if (statement instanceof GrVariableDeclaration) {
        GrVariableDeclaration declaration = (GrVariableDeclaration) statement;
        for (GrVariable variable : declaration.getVariables()) {
          if (varName.equals(variable.getName())) return true;
        }
      }
    }
    return ResolveUtil.resolveProperty(statements[0], varName) == null;
  }

  private static boolean containVariableDeclaration(@NotNull GrStatement[] statements, @NotNull String varName) {
    GroovyPsiElement element = ResolveUtil.resolveProperty(statements[0], varName);
    if (element == null) return false;
    for (GrStatement statement : statements) {
      if (statement.getTextRange().contains(element.getTextRange())) {
        return true;
      }
    }
    return false;
  }

  static void renameParameterOccurrences(GrMethod method, ExtractMethodInfoHelper helper) throws IncorrectOperationException {
    GrOpenBlock block = method.getBlock();
    if (block == null) return;
    GrStatement[] statements = block.getStatements();

    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    for (ParameterInfo info : helper.getParameterInfos()) {
      final String oldName = info.getOldName();
      final String newName = info.getName();
      final ArrayList<GrExpression> result = new ArrayList<GrExpression>();
      if (!oldName.equals(newName)) {
        for (final GrStatement statement : statements) {
          statement.accept(new PsiRecursiveElementVisitor() {
            public void visitElement(final PsiElement element) {
              super.visitElement(element);
              if (element instanceof GrReferenceExpression) {
                GrReferenceExpression expr = (GrReferenceExpression) element;
                if (!expr.isQualified() && oldName.equals(expr.getName())) {
                  result.add(expr);
                }
              }
            }
          });
          for (GrExpression expr : result) {
            expr.replaceWithExpression(factory.createExpressionFromText(newName), false);
          }
        }
      }
    }
  }

  static GrMethod createMethodByHelper(@NotNull String name, ExtractMethodInfoHelper helper) {
    StringBuffer buffer = new StringBuffer();

    //Add signature
    PsiType type = helper.getOutputType();
    final PsiPrimitiveType outUnboxed = PsiPrimitiveType.getUnboxedType(type);
    if (outUnboxed != null) type = outUnboxed;
    String typeText = getTypeString(helper, false);
    buffer.append(getModifierString(helper));
    buffer.append(typeText);
    buffer.append(name);
    buffer.append("(");
    for (String param : getParameterString(helper)) {
      buffer.append(param);
    }
    buffer.append(") { \n");

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    String outputName = helper.getOutputName();

    ParameterInfo[] infos = helper.getParameterInfos();
    boolean outputIsParameter = false;
    if (outputName != null) {
      for (ParameterInfo info : infos) {
        if (outputName.equals(info.getOldName())) {
          outputIsParameter = true;
        }
      }
    }
    if (type != PsiType.VOID && outputName != null && !outputIsParameter &&
        !mustAddVariableDeclaration(helper.getStatements(), outputName) &&
        !containVariableDeclaration(helper.getStatements(), outputName)) {
      GrVariableDeclaration decl = factory.createVariableDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, null, type, outputName);
      buffer.append(decl.getText()).append("\n");
    }
    if (!ExtractMethodUtil.isSingleExpression(helper.getStatements())) {
      for (PsiElement element : helper.getInnerElements()) {
        buffer.append(element.getText());
      }
      //append return statement
      if (type != PsiType.VOID && outputName != null) {
        buffer.append("\n return ");
        buffer.append(outputName);
      }
    } else {
      GrExpression expr = (GrExpression)PsiUtil.skipParentheses((GrExpression)helper.getStatements()[0], false);
      buffer.append(PsiType.VOID.equals(type) ? "" : "return ").append(expr != null ? expr.getText() : "");
    }

    buffer.append("\n}");

    String methodText = buffer.toString();
    GrMethod method = factory.createMethodFromText(methodText);
    assert method != null;
    return method;
  }

  static String[] getParameterString(ExtractMethodInfoHelper helper) {
    int i = 0;
    ParameterInfo[] infos = helper.getParameterInfos();
    int number = 0;
    for (ParameterInfo info : infos) {
      if (info.passAsParameter()) number++;
    }
    ArrayList<String> params = new ArrayList<String>();
    for (ParameterInfo info : infos) {
      if (info.passAsParameter()) {
        PsiType paramType = info.getType();
        final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(paramType);
        if (unboxed != null) paramType = unboxed;
        String paramTypeText = paramType == null || paramType.equalsToText("java.lang.Object") ? "" : paramType.getCanonicalText() + " ";
        params.add(paramTypeText + info.getName() + (i < number - 1 ? ", " : ""));
        i++;
      }
    }
    return ArrayUtil.toStringArray(params);
  }

  static String getTypeString(ExtractMethodInfoHelper helper, boolean forPresentation) {
    PsiType type = helper.getOutputType();
    final PsiPrimitiveType outUnboxed = PsiPrimitiveType.getUnboxedType(type);
    if (outUnboxed != null) type = outUnboxed;
    String typeText = forPresentation ? type.getPresentableText() : type.getCanonicalText();
    String returnType = typeText == null || typeText.equals("void") || typeText.equals("Object") || !helper.specifyType() ? "" : typeText;
    if (returnType.length() == 0) {
      typeText = "def ";
    } else {
      typeText = returnType + " ";
    }
    return typeText;
  }

  static GrStatement[] getStatementsByElements(PsiElement[] elements) {
    ArrayList<GrStatement> statementList = new ArrayList<GrStatement>();
    for (PsiElement element : elements) {
      if (element instanceof GrStatement) {
        statementList.add(((GrStatement) element));
      }
    }
    return statementList.toArray(new GrStatement[statementList.size()]);
  }

  static PsiElement[] getElementsInOffset(PsiFile file, int startOffset, int endOffset) {
    PsiElement[] elements;
    GrExpression expr = GroovyRefactoringUtil.findElementInRange(((GroovyFileBase) file), startOffset, endOffset, GrExpression.class);

    if (expr != null) {
      PsiElement parent = expr.getParent();
      if (expr.getParent() instanceof GrMethodCallExpression || parent instanceof GrIndexProperty) {
        expr = ((GrExpression) expr.getParent());
      }
      elements = new PsiElement[]{expr};
    } else {
      elements = GroovyRefactoringUtil.findStatementsInRange(file, startOffset, endOffset, true);
    }
    return elements;
  }

  @Nullable
  static GrMemberOwner getMemberOwner(GrStatement statement) {
    PsiElement parent = statement.getParent();
    while (parent != null && !(parent instanceof GrMemberOwner)) {
      if (parent instanceof GroovyFileBase) return (GrMemberOwner) ((GroovyFileBase) parent).getScriptClass();
      parent = parent.getParent();
    }
    return parent instanceof GrMemberOwner ? ((GrMemberOwner) parent) : null;
  }

  @Nullable
  static GrStatementOwner getDeclarationOwner(GrStatement statement) {
    PsiElement parent = statement.getParent();
    return parent instanceof GrStatementOwner ? ((GrStatementOwner) parent) : null;
  }

  static boolean isSingleExpression(GrStatement[] statements) {
    return statements.length == 1 && statements[0] instanceof GrExpression &&
        !(statements[0].getParent() instanceof GrVariableDeclarationOwner && statements[0] instanceof GrAssignmentExpression);
  }

  static GrMethodCallExpression createMethodCallByHelper(@NotNull String name, ExtractMethodInfoHelper helper) {
    StringBuffer buffer = new StringBuffer();
    buffer.append(name).append("(");
    int number = 0;
    for (ParameterInfo info : helper.getParameterInfos()) {
      if (info.passAsParameter()) number++;
    }
    int i = 0;
    String[] argumentNames = helper.getArgumentNames();
    for (String argName : argumentNames) {
      if (argName.length() > 0) {
        buffer.append(argName);
        if (i < number - 1) {
          buffer.append(",");
        }
        i++;
      }
    }

    buffer.append(")");
    String callText = buffer.toString();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(helper.getProject());
    GrExpression expr = factory.createExpressionFromText(callText);
    assert expr instanceof GrMethodCallExpression;
    return ((GrMethodCallExpression) expr);
  }

  static int getCaretOffset(@NotNull GrStatement statement) {
    if (statement instanceof GrVariableDeclaration) {
      GrVariable[] variables = ((GrVariableDeclaration) statement).getVariables();
      if (variables.length > 0) {
        GrExpression initializer = variables[0].getInitializerGroovy();
        if (initializer != null) {
          return initializer.getTextOffset();
        }
      }
    } else if (statement instanceof GrAssignmentExpression) {
      GrExpression value = ((GrAssignmentExpression) statement).getRValue();
      if (value != null) {
        return value.getTextOffset();
      }
    } else if (statement instanceof GrMethodCallExpression) {
      return statement.getTextOffset();
    }
    return statement.getTextOffset();
  }

  static boolean canBeStatic(GrStatement statement) {
    PsiElement parent = statement.getParent();
    while (parent != null && !(parent instanceof PsiFile)) {
      if (parent instanceof GrMethod) {
        return ((GrMethod) parent).hasModifierProperty(PsiModifier.STATIC);
      }
      parent = parent.getParent();
    }
    return false;
  }

  static String getModifierString(ExtractMethodInfoHelper helper) {
    String visibility = helper.getVisibility();
    assert visibility != null && visibility.length() > 0;
    visibility = visibility.equals(PsiModifier.PUBLIC) ? "" : visibility + " ";
    return visibility + (helper.isStatic() ? "static " : "");
  }

  static boolean isReturnStatement(GrStatement statement, Collection<GrReturnStatement> returnStatements) {
    if (statement instanceof GrReturnStatement) return true;
    if (statement instanceof GrIfStatement) {
      boolean checked = GroovyInlineMethodUtil.checkTailIfStatement(((GrIfStatement) statement), returnStatements);
      return checked & returnStatements.size() == 0;

    }
    return false;
  }

}
