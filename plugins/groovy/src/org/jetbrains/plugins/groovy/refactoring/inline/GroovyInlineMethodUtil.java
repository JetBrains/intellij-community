// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.inline.InlineOptionsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.*;

import static org.jetbrains.annotations.Nls.Capitalization.Title;

public final class GroovyInlineMethodUtil {
  private GroovyInlineMethodUtil() {
  }

  @NotNull
  public static InlineHandler.Settings inlineMethodSettings(GrMethod method, Editor editor, boolean invokedOnReference) {

    final Project project = method.getProject();
    if (method.isConstructor()) {
      String message = GroovyRefactoringBundle.message("refactoring.cannot.be.applied.to.constructors", getRefactoringName());
      showErrorMessage(message, project, editor);
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }

    if (invokedOnReference) {
      PsiReference reference = editor != null ? TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset()) : null;
      if (reference == null) return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;

      PsiElement element = reference.getElement();

      if (!(element instanceof GrExpression && element.getParent() instanceof GrCallExpression call)) {
        String message = GroovyRefactoringBundle.message("refactoring.is.available.only.for.method.calls", getRefactoringName());
        showErrorMessage(message, project, editor);
        return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
      }

      if (PsiTreeUtil.getParentOfType(element, GrParameter.class) != null) {
        String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.in.parameter.initializers", getRefactoringName());
        showErrorMessage(message, project, editor);
        return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
      }


      GroovyRefactoringUtil.highlightOccurrences(project, editor, new GrExpression[]{call});
      if (hasBadReturns(method) && !isTailMethodCall(call)) {
        String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow",
                                                         getRefactoringName());
        showErrorMessage(message, project, editor);
        return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
      }
    }
    else {
      if (hasBadReturns(method)) {
        String message = GroovyRefactoringBundle.message("refactoring.is.not.supported.when.return.statement.interrupts.the.execution.flow",
                                                         getRefactoringName());
        showErrorMessage(message, project, editor);
        return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
      }
    }

    if (method.getBlock() == null) {
      String message = method.hasModifierProperty(PsiModifier.ABSTRACT)
                       ? GroovyRefactoringBundle.message("refactoring.cannot.be.applied.to.abstract.methods", getRefactoringName())
                       : GroovyRefactoringBundle.message("refactoring.cannot.be.applied.no.sources.attached", getRefactoringName());
      showErrorMessage(message, project, editor);
      return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
    }

    return inlineMethodDialogResult(method, project, invokedOnReference);
  }

  /**
   * Checks whether given method call is tail call of other method or closure
   *
   * @param call [tail?] Method call
   */
  static boolean isTailMethodCall(GrCallExpression call) {
    GrStatement stmt = call;
    PsiElement parent = call.getParent();

    // return statement
    if (parent instanceof GrReturnStatement) {
      stmt = ((GrReturnStatement) parent);
      parent = parent.getParent();
    }
    // method body result
    if (parent instanceof GrOpenBlock) {
      if (parent.getParent() instanceof GrMethod) {
        GrStatement[] statements = ((GrOpenBlock) parent).getStatements();
        return statements.length > 0 && stmt == statements[statements.length - 1];

      }
    }
    // closure result
    if (parent instanceof GrClosableBlock) {
      GrStatement[] statements = ((GrClosableBlock) parent).getStatements();
      return statements.length > 0 && stmt == statements[statements.length - 1];
    }

    // todo add for inner method block statements
    // todo test me!
    if (stmt instanceof GrReturnStatement) {
      GrMethod method = PsiTreeUtil.getParentOfType(stmt, GrMethod.class);
      if (method != null) {
        Collection<GrStatement> returnStatements = ControlFlowUtils.collectReturns(method.getBlock());
        return returnStatements.contains(stmt) && !hasBadReturns(method);
      }
    }

    return false;
  }

  /**
   * Shows dialog with question to inline
   */
  @NotNull
  private static InlineHandler.Settings inlineMethodDialogResult(GrMethod method, Project project, boolean invokedOnReference) {
    Application application = ApplicationManager.getApplication();
    if (!application.isUnitTestMode()) {
      final InlineMethodDialog dialog = new InlineMethodDialog(project, method, invokedOnReference, checkMethodForRecursion(method));

      if (!dialog.showAndGet()) {
        WindowManager.getInstance().getStatusBar(project)
          .setInfo(GroovyRefactoringBundle.message("press.escape.to.remove.the.highlighting"));
        return InlineHandler.Settings.CANNOT_INLINE_SETTINGS;
      }
      else {
        return new InlineHandler.Settings() {
          @Override
          public boolean isOnlyOneReferenceToInline() {
            return dialog.isInlineThisOnly();
          }
        };
      }
    }
    return new InlineHandler.Settings() {
      @Override
      public boolean isOnlyOneReferenceToInline() {
        return true;
      }
    };

  }

  private static boolean hasBadReturns(GrMethod method) {
    Collection<GrStatement> returnStatements = ControlFlowUtils.collectReturns(method.getBlock());
    GrOpenBlock block = method.getBlock();
    if (block == null || returnStatements.isEmpty()) return false;
    boolean checked = checkTailOpenBlock(block, returnStatements);
    return !(checked && returnStatements.isEmpty());
  }

  public static boolean checkTailIfStatement(GrIfStatement ifStatement, Collection<GrStatement> returnStatements) {
    GrStatement thenBranch = ifStatement.getThenBranch();
    GrStatement elseBranch = ifStatement.getElseBranch();
    if (elseBranch == null) return false;
    boolean tb = false;
    boolean eb = false;
    if (thenBranch instanceof GrReturnStatement) {
      tb = returnStatements.remove(thenBranch);
    } else if (thenBranch instanceof GrBlockStatement) {
      tb = checkTailOpenBlock(((GrBlockStatement) thenBranch).getBlock(), returnStatements);
    }
    if (elseBranch instanceof GrReturnStatement) {
      eb = returnStatements.remove(elseBranch);
    } else if (elseBranch instanceof GrBlockStatement) {
      eb = checkTailOpenBlock(((GrBlockStatement) elseBranch).getBlock(), returnStatements);
    }

    return tb && eb;
  }

  private static boolean checkTailOpenBlock(GrOpenBlock block, Collection<GrStatement> returnStatements) {
    if (block == null) return false;
    GrStatement[] statements = block.getStatements();
    if (statements.length == 0) return false;
    GrStatement last = statements[statements.length - 1];
    if (returnStatements.contains(last)) {
      returnStatements.remove(last);
      return true;
    }
    if (last instanceof GrIfStatement) {
      return checkTailIfStatement(((GrIfStatement) last), returnStatements);
    }
    return false;

  }

  private static void showErrorMessage(@DialogMessage String message, final Project project, Editor editor) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INLINE_METHOD);
  }

  public static Collection<ReferenceExpressionInfo> collectReferenceInfo(GrMethod method) {
    ArrayList<ReferenceExpressionInfo> list = new ArrayList<>();
    collectReferenceInfoImpl(list, method, method);
    return list;
  }

  private static void collectReferenceInfoImpl(Collection<ReferenceExpressionInfo> infos, PsiElement elem, GrMethod method) {
    if (elem instanceof GrReferenceExpression expr) {
      PsiReference ref = expr.getReference();
      if (ref != null) {
        PsiElement declaration = ref.resolve();
        if (declaration instanceof GrMember member) {
          int offsetInMethod = expr.getTextRange().getStartOffset() - method.getTextRange().getStartOffset();
          infos.add(new ReferenceExpressionInfo(expr, offsetInMethod, member, member.getContainingClass()));
        }
      }
    }
    for (PsiElement element : elem.getChildren()) {
      collectReferenceInfoImpl(infos, element, method);
    }

  }

  public static boolean isSimpleReference(GrExpression qualifier) {
    if (!(qualifier instanceof GrReferenceExpression)) return false;
    GrExpression qual = ((GrReferenceExpression) qualifier).getQualifierExpression();
    return qual == null || isSimpleReference(qual);
  }

  static class ReferenceExpressionInfo {
    public final PsiMember declaration;
    public final GrReferenceExpression expression;
    public final int offsetInMethod;
    public final PsiClass containingClass;

    @Nullable
    public @Nls String getPresentation() {
      return declaration.getName();
    }

    public boolean isStatic() {
      return declaration.hasModifierProperty(PsiModifier.STATIC);
    }

    ReferenceExpressionInfo(GrReferenceExpression expression, int offsetInMethod, PsiMember declaration, PsiClass containingClass) {
      this.expression = expression;
      this.offsetInMethod = offsetInMethod;
      this.declaration = declaration;
      this.containingClass = containingClass;
    }
  }


  static void addQualifiersToInnerReferences(GrMethod method, Collection<? extends ReferenceExpressionInfo> infos, @NotNull GrExpression qualifier)
      throws IncorrectOperationException {
    Set<GrReferenceExpression> exprs = new HashSet<>();
    for (ReferenceExpressionInfo info : infos) {
      PsiReference ref = method.findReferenceAt(info.offsetInMethod);
      if (ref != null && ref.getElement() instanceof GrReferenceExpression refExpr) {
        if (refExpr.getQualifierExpression() == null) {
          exprs.add(refExpr);
        }
      }
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(qualifier.getProject());
    for (GrReferenceExpression expr : exprs) {
      GrExpression qual = factory.createExpressionFromText(qualifier.getText());
      expr.setQualifier(qual);
    }
  }

  private static boolean checkMethodForRecursion(GrMethod method) {
    return checkCalls(method.getBlock(), method);
  }

  private static boolean checkCalls(PsiElement scope, PsiMethod method) {
    if (scope instanceof GrMethodCall) {
      PsiMethod refMethod = ((GrMethodCall)scope).resolveMethod();
      if (method.equals(refMethod)) return true;
    }

    for (PsiElement child = scope.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (checkCalls(child, method)) return true;
    }

    return false;
  }


  static class InlineMethodDialog extends InlineOptionsDialog {
    private final boolean myAllowInlineThisOnly;

    private final PsiMethod myMethod;

    InlineMethodDialog(Project project,
                              PsiMethod method,
                              boolean invokedOnReference,
                              final boolean allowInlineThisOnly) {
      super(project, true, method);
      myMethod = method;
      myAllowInlineThisOnly = allowInlineThisOnly;
      myInvokedOnReference = invokedOnReference;

      setTitle(getRefactoringName());

      init();
    }

    @Override
    protected String getBorderTitle() {
      return GroovyRefactoringBundle.message("inline.method.border.title");
    }

    @Override
    protected String getNameLabelText() {
      return GroovyRefactoringBundle.message("inline.method.label", GroovyRefactoringUtil.getMethodSignature(myMethod));
    }

    @Override
    protected String getInlineAllText() {
      return myMethod.isWritable()
             ? GroovyRefactoringBundle.message("all.invocations.and.remove.the.method")
             : GroovyRefactoringBundle.message("all.invocations.in.project");
    }

    @Override
    protected String getInlineThisText() {
      return GroovyRefactoringBundle.message("this.invocation.only.and.keep.the.method");
    }

    @Override
    protected boolean isInlineThis() {
      return false;
    }

    @Override
    protected void doAction() {
      if (getOKAction().isEnabled()) {
        close(OK_EXIT_CODE);
      }
    }

    @Override
    protected String getHelpId() {
      return HelpID.INLINE_METHOD;
    }

    @Override
    protected boolean canInlineThisOnly() {
      return myAllowInlineThisOnly;
    }
  }

  /**
   * Inline method call's arguments as its parameters
   *
   * @param call   method call
   * @param method given method
   */
  public static void replaceParametersWithArguments(GrCallExpression call, GrMethod method) throws IncorrectOperationException {
    GrParameter[] parameters = method.getParameters();
    if (parameters.length == 0) return;

    GrArgumentList argumentList = call.getArgumentList();
    if (argumentList == null) {
      setDefaultValuesToParameters(method, null, call);
      return;
    }

    Project project = call.getProject();

    final GroovyResolveResult resolveResult = call.advancedResolve();
    GrSignature signature = GrClosureSignatureUtil.createSignature(method, resolveResult.getSubstitutor());

    GrClosureSignatureUtil.ArgInfo<PsiElement>[] infos = GrClosureSignatureUtil.mapParametersToArguments(
      signature,
      call.getNamedArguments(),
      call.getExpressionArguments(),
      call.getClosureArguments(),
      call, true, false
    );
    if (infos == null) return;

    for (int i = 0; i < infos.length; i++) {
      GrClosureSignatureUtil.ArgInfo<PsiElement> argInfo = infos[i];
      GrParameter parameter = parameters[i];

      final GrExpression arg = inferArg(signature, parameters, parameter, argInfo, project);
      if (arg != null) {
        replaceAllOccurrencesWithExpression(method, call, arg, parameter);
      }
    }
  }

  @Nullable
  private static GrExpression inferArg(GrSignature signature,
                                       GrParameter[] parameters,
                                       GrParameter parameter,
                                       GrClosureSignatureUtil.ArgInfo<PsiElement> argInfo,
                                       Project project) {
    if (argInfo == null) return null;
    List<PsiElement> arguments = argInfo.args;

    if (argInfo.isMultiArg) { //arguments for Map and varArg
      final PsiType type = parameter.getDeclaredType();
      return GroovyRefactoringUtil.generateArgFromMultiArg(signature.getSubstitutor(), arguments, type, project);
    }
    else {  //arguments for simple parameters
      if (arguments.size() == 1) { //arg exists
        PsiElement arg = arguments.iterator().next();
        if (isVararg(parameter, parameters)) {
          if (arg instanceof GrSafeCastExpression) {
            PsiElement expr = ((GrSafeCastExpression)arg).getOperand();
            if (expr instanceof GrListOrMap && !((GrListOrMap)expr).isMap()) {
              return ((GrListOrMap)expr);
            }
          }
        }

        return (GrExpression)arg;
      }
      else { //arg is skipped. Parameter is optional
        return parameter.getInitializerGroovy();
      }
    }
  }

  private static boolean isVararg(GrParameter p, GrParameter[] parameters) {
    return parameters[parameters.length - 1] == p && p.getType() instanceof PsiArrayType;
  }

  /**
   * replaces parameter occurrences in method with its default values (if it's possible)
   *
   * @param method     given method
    * @param nameFilter specified parameter names (which ma have default initializers)
   */
  private static void setDefaultValuesToParameters(GrMethod method, @Nullable Collection<String> nameFilter, GrCallExpression call) throws IncorrectOperationException {
    if (nameFilter == null) {
      nameFilter = new ArrayList<>();
      for (GrParameter parameter : method.getParameters()) {
        nameFilter.add(parameter.getName());
      }
    }
    GrParameter[] parameters = method.getParameters();
    for (GrParameter parameter : parameters) {
      GrExpression initializer = parameter.getInitializerGroovy();
      if (initializer != null && nameFilter.contains(parameter.getName())) {
        replaceAllOccurrencesWithExpression(method, call, initializer, parameter);
      }
    }
  }

  private static void replaceAllOccurrencesWithExpression(GrMethod method,
                                                          GrCallExpression call,
                                                          GrExpression oldExpression,
                                                          GrParameter parameter) {
    Collection<PsiReference> refs = ReferencesSearch.search(parameter, new LocalSearchScope(method), false).findAll();

    final GroovyPsiElementFactory elementFactory = GroovyPsiElementFactory.getInstance(call.getProject());
    GrExpression expression = elementFactory.createExpressionFromText(oldExpression.getText());


    if (GroovyRefactoringUtil.hasSideEffect(expression) && refs.size() > 1 || !hasUnresolvableWriteAccess(refs, oldExpression)) {
      final String oldName = parameter.getName();
      final String newName = InlineMethodConflictSolver.suggestNewName(oldName, method, call);

      expression = elementFactory.createExpressionFromText(newName);
      final GrOpenBlock body = method.getBlock();
      final GrStatement[] statements = body.getStatements();
      GrStatement anchor = null;
      if (statements.length > 0) {
        anchor = statements[0];
      }
      body.addStatementBefore(elementFactory.createStatementFromText(createVariableDefinitionText(parameter, oldExpression, newName)),
                              anchor);
    }

    for (PsiReference ref : refs) {
      PsiElement element = ref.getElement();
      if (element instanceof GrReferenceExpression) {
        ((GrReferenceExpression)element).replaceWithExpression(expression, true);
      }
    }
  }

  private static String createVariableDefinitionText(GrParameter parameter, GrExpression expression, String varName) {
    StringBuilder buffer = new StringBuilder();
    final PsiModifierList modifierList = parameter.getModifierList();
    buffer.append(modifierList.getText().trim());
    if (buffer.length() > 0) buffer.append(' ');

    final GrTypeElement typeElement = parameter.getTypeElementGroovy();
    if (typeElement != null) {
      buffer.append(typeElement.getText()).append(' ');
    }

    if (buffer.length() == 0) {
      buffer.append("def ");
    }
    buffer.append(varName).append(" = ").append(expression.getText());
    return buffer.toString();
  }

  /**
   * @param refs collection of references to method parameters. It is considered that these references have no qualifiers
   */
  private static boolean containsWriteAccess(Collection<PsiReference> refs) {
    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      final PsiElement parent = element.getParent();
      if (parent instanceof GrAssignmentExpression && ((GrAssignmentExpression)parent).getLValue() == element) return true;
      if (parent instanceof GrUnaryExpression) return true;
    }
    return false;
  }

  private static boolean hasUnresolvableWriteAccess(Collection<PsiReference> refs, GrExpression expression) {
    if (containsWriteAccess(refs)) {
      if (expression instanceof GrReferenceExpression) {
        final PsiElement resolved = ((GrReferenceExpression)expression).resolve();

        if (resolved instanceof GrVariable && !(resolved instanceof PsiField)) {
          final boolean isFinal = ((GrVariable)resolved).hasModifierProperty(PsiModifier.FINAL);
          if (!isFinal) {
            final PsiReference lastRef =
              Collections.max(ReferencesSearch.search(resolved).findAll(),
                              Comparator.comparingInt(o -> o.getElement().getTextRange().getStartOffset()));
            return lastRef.getElement() == expression;
          }
        }
      }
      return false;
    }

    return true;
  }

  public static @Nls(capitalization = Title) String getRefactoringName() {
    return GroovyRefactoringBundle.message("inline.method.title");
  }
}
