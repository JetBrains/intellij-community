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

package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.daemon.impl.quickfix.AddMethodBodyFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicMethodFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyImportsTracker;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.*;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrFlowInterruptingStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.noncode.GrInheritConstructorContributor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;
import org.jetbrains.plugins.groovy.overrideImplement.GroovyOverrideImplementUtil;
import org.jetbrains.plugins.groovy.overrideImplement.quickFix.ImplementMethodsQuickFix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class GroovyAnnotator extends GroovyElementVisitor implements Annotator {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.GroovyAnnotator");

  private AnnotationHolder myHolder;

  private static boolean isDocCommentElement(PsiElement element) {
    if (element == null) return false;
    ASTNode node = element.getNode();
    return node != null && PsiTreeUtil.getParentOfType(element, GrDocComment.class) != null || element instanceof GrDocComment;
  }

  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof GroovyPsiElement) {
      myHolder = holder;
      ((GroovyPsiElement)element).accept(this);
      myHolder = null;
    }
  }

  @Override
  public void visitElement(GroovyPsiElement element) {
    if (element.getParent() instanceof GrDocReferenceElement) {
      checkGrDocReferenceElement(myHolder, element);
    }
    else {
      final ASTNode node = element.getNode();
      if (!(element instanceof PsiWhiteSpace) &&
          !GroovyTokenTypes.COMMENT_SET.contains(node.getElementType()) &&
          element.getContainingFile() instanceof GroovyFile &&
          !isDocCommentElement(element)) {
        GroovyImportsTracker.getInstance(element.getProject()).markFileAnnotated((GroovyFile)element.getContainingFile());
      }
    }
  }

  @Override
  public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
    final PsiElement parent = refElement.getParent();
    GroovyResolveResult resolveResult = refElement.advancedResolve();
    highlightAnnotation(myHolder, refElement, resolveResult);
    registerUsedImport(refElement, resolveResult);
    highlightAnnotation(myHolder, refElement, resolveResult);
    if (refElement.getReferenceName() != null) {

      if (parent instanceof GrImportStatement && ((GrImportStatement)parent).isStatic() && refElement.multiResolve(false).length > 0) {
        return;
      }

      checkSingleResolvedElement(myHolder, refElement, resolveResult, true);
    }
  }

  @Override
  public void visitReferenceExpression(GrReferenceExpression referenceExpression) {
    GroovyResolveResult resolveResult = referenceExpression.advancedResolve();
    GroovyResolveResult[] results = referenceExpression.multiResolve(false); //cached
    for (GroovyResolveResult result : results) {
      registerUsedImport(referenceExpression, result);
    }

    PsiElement resolved = resolveResult.getElement();
    final PsiElement parent = referenceExpression.getParent();
    if (resolved != null) {
      if (resolved instanceof PsiMember) {
        highlightMemberResolved(myHolder, referenceExpression, ((PsiMember)resolved));
      }
      if (!resolveResult.isAccessible()) {
        String message = GroovyBundle.message("cannot.access", referenceExpression.getReferenceName());
        final Annotation annotation = myHolder.createWarningAnnotation(referenceExpression.getReferenceNameElement(), message);
        if (resolved instanceof PsiMember) {
          registerAccessFix(annotation, referenceExpression, ((PsiMember)resolved));
        }
      }
      if (!resolveResult.isStaticsOK() && resolved instanceof PsiModifierListOwner) {
        if (!((PsiModifierListOwner)resolved).hasModifierProperty(GrModifier.STATIC)) {
          myHolder.createErrorAnnotation(referenceExpression,
                                         GroovyBundle.message("cannot.reference.nonstatic", referenceExpression.getReferenceName()));
        }
      }
    }
    else {
      GrExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier == null && isDeclarationAssignment(referenceExpression)) return;

      if (parent instanceof GrReferenceExpression && "class".equals(((GrReferenceExpression)parent).getReferenceName())) {
        checkSingleResolvedElement(myHolder, referenceExpression, resolveResult, false);
      }
    }

    if (parent instanceof GrCall) {
      if (resolved == null && results.length > 0) {
        resolved = results[0].getElement();
        resolveResult = results[0];
      }
      if (resolved instanceof PsiMethod && resolved.getUserData(GrMethod.BUILDER_METHOD) == null) {
        checkMethodApplicability(resolveResult, referenceExpression, myHolder);
      }
      else {
        checkClosureApplicability(resolveResult, referenceExpression.getType(), referenceExpression, myHolder);
      }
    }
    if (isDeclarationAssignment(referenceExpression) || resolved instanceof PsiPackage) return;

    if (resolved == null) {
      PsiElement refNameElement = referenceExpression.getReferenceNameElement();
      PsiElement elt = refNameElement == null ? referenceExpression : refNameElement;
      Annotation annotation = myHolder.createInfoAnnotation(elt, null);
      final GrExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier == null) {
        if (!(parent instanceof GrCall)) {
          registerCreateClassByTypeFix(referenceExpression, annotation);
          registerAddImportFixes(referenceExpression, annotation);
        }
        else {
          registerStaticImportFix(referenceExpression, annotation);
        }
      }
      else {
        if (qualifier.getType() == null) {
          return;
        }
      }
      registerReferenceFixes(referenceExpression, annotation);
      annotation.setTextAttributes(DefaultHighlighter.UNRESOLVED_ACCESS);
    }
  }

  private static void registerAccessFix(Annotation annotation, GrReferenceExpression place, PsiMember refElement) {
    if (refElement instanceof PsiCompiledElement) return;
    PsiModifierList modifierList = refElement.getModifierList();
    if (modifierList == null) return;

    try {
      Project project = refElement.getProject();
      JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
      PsiModifierList modifierListCopy = facade.getElementFactory().createFieldFromText("int a;", null).getModifierList();
      modifierListCopy.setModifierProperty(PsiModifier.STATIC, modifierList.hasModifierProperty(PsiModifier.STATIC));
      @Modifier String minModifier = PsiModifier.PROTECTED;
      if (refElement.hasModifierProperty(PsiModifier.PROTECTED)) {
        minModifier = PsiModifier.PUBLIC;
      }
      String[] modifiers = {PsiModifier.PROTECTED, PsiModifier.PUBLIC, PsiModifier.PACKAGE_LOCAL};
      PsiClass accessObjectClass = PsiTreeUtil.getParentOfType(place, PsiClass.class, false);
      if (accessObjectClass == null) {
        accessObjectClass = ((GroovyFile)place.getContainingFile()).getScriptClass();
      }
      for (int i = ArrayUtil.indexOf(modifiers, minModifier); i < modifiers.length; i++) {
        String modifier = modifiers[i];
        modifierListCopy.setModifierProperty(modifier, true);
        if (facade.getResolveHelper().isAccessible(refElement, modifierListCopy, place, accessObjectClass, null)) {
          IntentionAction fix = new GrModifierFix(refElement, refElement.getModifierList(), modifier, true, true);
          annotation.registerFix(fix);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private static void registerStaticImportFix(GrReferenceExpression referenceExpression, Annotation annotation) {
    final String referenceName = referenceExpression.getReferenceName();
    //noinspection ConstantConditions
    if (StringUtil.isEmpty(referenceName)) {
      return;
    }

    annotation.registerFix(new GroovyStaticImportMethodFix((GrMethodCall)referenceExpression.getParent()));
  }

  @Override
  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    checkTypeDefinition(myHolder, typeDefinition);
    checkTypeDefinitionModifiers(myHolder, typeDefinition);

    final GrTypeDefinitionBody body = typeDefinition.getBody();
    if (body != null) checkDuplicateMethod(body.getGroovyMethods(), myHolder);
    checkImplementedMethodsOfClass(myHolder, typeDefinition);
    checkConstructors(myHolder, typeDefinition);
  }

  private static void checkConstructors(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.isEnum() || typeDefinition.isInterface() || typeDefinition.isAnonymous()) return;
    final PsiClass superClass = typeDefinition.getSuperClass();
    if (superClass == null) return;

    if (GrInheritConstructorContributor.hasInheritConstructorsAnnotation(typeDefinition)) return;

    PsiMethod defConstructor = getDefaultConstructor(superClass);
    boolean hasImplicitDefConstructor = superClass.getConstructors().length ==0;

    final PsiMethod[] constructors = typeDefinition.getConstructors();
    final String qName = superClass.getQualifiedName();
    if (constructors.length == 0) {
      if (!hasImplicitDefConstructor && (defConstructor == null || !PsiUtil.isAccessible(typeDefinition, defConstructor))) {
        final TextRange range = getHeaderTextRange(typeDefinition);
        holder.createErrorAnnotation(range, GroovyBundle.message("there.is.no.default.constructor.available.in.class.0", qName));
      }
      return;
    }
    for (PsiMethod method : constructors) {
      if (method instanceof GrMethod) {
        final GrOpenBlock block = ((GrMethod)method).getBlock();
        if (block == null) continue;
        final GrStatement[] statements = block.getStatements();
        if (statements.length > 0) {
          if (statements[0] instanceof GrConstructorInvocation) continue;
        }

        if (!hasImplicitDefConstructor && (defConstructor == null || !PsiUtil.isAccessible(typeDefinition, defConstructor))) {
          final TextRange range = getMethodHeaderTextRange((GrMethod)method);
          holder.createErrorAnnotation(range, GroovyBundle.message("there.is.no.default.constructor.available.in.class.0", qName));
        }
      }
    }
  }

  @Override
  public void visitMethod(GrMethod method) {
    checkMethodDefinitionModifiers(myHolder, method);
    checkInnerMethod(myHolder, method);
  }

  @Nullable
  private static PsiMethod getDefaultConstructor(PsiClass clazz) {
    final String className = clazz.getName();
    if (className == null) return null;
    final PsiMethod[] byName = clazz.findMethodsByName(className, true);
    if (byName.length == 0) return null;
    for (PsiMethod method : byName) {
      if (method.getParameterList().getParametersCount() == 0) return method;
    }
    return null;
  }

  @Override
  public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {

    PsiElement parent = variableDeclaration.getParent();
    assert parent != null;

    PsiElement typeDef = parent.getParent();
    if (typeDef != null && typeDef instanceof GrTypeDefinition) {
      PsiModifierList modifiersList = variableDeclaration.getModifierList();
      final GrMember member = variableDeclaration.getMembers()[0];
      checkAccessModifiers(myHolder, modifiersList, member);
      checkDuplicateModifiers(myHolder, variableDeclaration.getModifierList(), member);

      if (modifiersList.hasExplicitModifier(GrModifier.VOLATILE) && modifiersList.hasExplicitModifier(GrModifier.FINAL)) {
        final Annotation annotation =
          myHolder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.volatile.and.final"));
        annotation.registerFix(new GrModifierFix(member, modifiersList, GrModifier.VOLATILE, true, false));
        annotation.registerFix(new GrModifierFix(member, modifiersList, GrModifier.FINAL, true, false));
      }

      if (modifiersList.hasExplicitModifier(GrModifier.NATIVE)) {
        final Annotation annotation = myHolder.createErrorAnnotation(modifiersList, GroovyBundle.message("variable.cannot.be.native"));
        annotation.registerFix(new GrModifierFix(member, modifiersList, GrModifier.NATIVE, true, false));
      }

      if (modifiersList.hasExplicitModifier(GrModifier.ABSTRACT)) {
        final Annotation annotation = myHolder.createErrorAnnotation(modifiersList, GroovyBundle.message("variable.cannot.be.abstract"));
        annotation.registerFix(new GrModifierFix(member, modifiersList, GrModifier.ABSTRACT, true, false));
      }
    }
  }

  @Override
  public void visitVariable(GrVariable variable) {
    if (variable instanceof GrMember) {
      highlightMember(myHolder, ((GrMember)variable));
      checkStaticDeclarationsInInnerClass((GrMember)variable, myHolder);
    }

    GroovyPsiElement duplicate =
      ResolveUtil
        .resolveExistingElement(variable, new DuplicateVariablesProcessor(variable), GrVariable.class, GrReferenceExpression.class);
    if (duplicate == null) {
      PsiElement context = variable;
      if (variable instanceof GrParameter) {
        final PsiElement parent = context.getContext().getContext();
        if (parent instanceof GrClosableBlock) {
          context = parent;
          duplicate = ResolveUtil
            .resolveExistingElement(context, new DuplicateVariablesProcessor(variable), GrVariable.class, GrReferenceExpression.class);
        }
      }
    }

    if (duplicate instanceof GrVariable) {
      if (duplicate instanceof GrField && !(variable instanceof GrField)) {
        myHolder
          .createWarningAnnotation(variable.getNameIdentifierGroovy(), GroovyBundle.message("field.already.defined", variable.getName()));
      }
      else {
        final String key = duplicate instanceof GrField ? "field.already.defined" : "variable.already.defined";
        myHolder.createErrorAnnotation(variable.getNameIdentifierGroovy(), GroovyBundle.message(key, variable.getName()));
      }
    }
  }

  @Override
  public void visitAssignmentExpression(GrAssignmentExpression expression) {
    GrExpression lValue = expression.getLValue();
    if (!PsiUtil.mightBeLVlaue(lValue)) {
      myHolder.createErrorAnnotation(lValue, GroovyBundle.message("invalid.lvalue"));
    }
  }

  @Override
  public void visitReturnStatement(GrReturnStatement returnStatement) {
    final GrExpression value = returnStatement.getReturnValue();
    if (value != null) {
      final PsiType type = value.getType();
      if (type != null) {
        final GrParametersOwner owner = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class, GrClosableBlock.class);
        if (owner instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)owner;
          if (method.isConstructor()) {
            myHolder.createErrorAnnotation(value, GroovyBundle.message("cannot.return.from.constructor"));
          }
          else {
            final PsiType methodType = method.getReturnType();
            if (methodType != null) {
              if (PsiType.VOID.equals(methodType)) {
                myHolder.createErrorAnnotation(value, GroovyBundle.message("cannot.return.from.void.method"));
              }
            }
          }
        }
      }
    }
  }

  @Override
  public void visitListOrMap(GrListOrMap listOrMap) {
    MultiMap<String, GrNamedArgument> map = new MultiMap<String, GrNamedArgument>();

    for (GrNamedArgument element : listOrMap.getNamedArguments()) {
      final GrArgumentLabel label = element.getLabel();
      if (label != null) {
        final String name = label.getName();
        if (name != null) {
          map.putValue(name, element);
        }
      }
    }

    for (String key : map.keySet()) {
      final Collection<GrNamedArgument> arguments = map.get(key);
      if (arguments.size() > 1) {
        final List<GrNamedArgument> args = new ArrayList<GrNamedArgument>(arguments);
        for (int i = 1; i < args.size(); i++) {
          GrNamedArgument namedArgument = args.get(i);
          myHolder.createWarningAnnotation(namedArgument.getLabel(), GroovyBundle.message("duplicate.element.in.the.map"));
        }
      }
    }
  }

  @Override
  public void visitNewExpression(GrNewExpression newExpression) {
    if (newExpression.getArrayCount() > 0) return;
    GrCodeReferenceElement refElement = newExpression.getReferenceElement();
    if (refElement == null) return;
    final PsiElement element = refElement.resolve();
    if (element instanceof PsiClass) {
      PsiClass clazz = (PsiClass)element;
      if (clazz.hasModifierProperty(GrModifier.ABSTRACT)) {
        if (newExpression.getAnonymousClassDefinition() == null) {
          String message = clazz.isInterface()
                           ? GroovyBundle.message("cannot.instantiate.interface", clazz.getName())
                           : GroovyBundle.message("cannot.instantiate.abstract.class", clazz.getName());
          myHolder.createErrorAnnotation(refElement, message);
        }
        return;
      }
      if (newExpression.getQualifier() != null) {
        if (clazz.hasModifierProperty(GrModifier.STATIC)) {
          myHolder.createErrorAnnotation(newExpression, GroovyBundle.message("qualified.new.of.static.class"));
        }
      }
      else {
        final PsiClass outerClass = clazz.getContainingClass();
        if (com.intellij.psi.util.PsiUtil.isInnerClass(clazz) && !PsiUtil.hasEnclosingInstanceInScope(outerClass, newExpression, true)) {
          myHolder.createErrorAnnotation(newExpression, GroovyBundle.message("cannot.reference.nonstatic", clazz.getQualifiedName()));
        }
      }
    }

    final GroovyResolveResult constructorResolveResult = newExpression.resolveConstructorGenerics();
    final PsiElement constructor = constructorResolveResult.getElement();
    if (constructor != null) {
      final GrArgumentList argList = newExpression.getArgumentList();
      if (argList != null &&
          argList.getExpressionArguments().length == 0 &&
          ((PsiMethod)constructor).getParameterList().getParametersCount() == 0) {
        checkDefaultMapConstructor(myHolder, argList, constructor);
      }
      else {
        checkMethodApplicability(constructorResolveResult, refElement, myHolder);
      }
    }
    else {
      final GroovyResolveResult[] results = newExpression.multiResolveConstructor();
      final GrArgumentList argList = newExpression.getArgumentList();
      PsiElement toHighlight = argList != null ? argList : refElement.getReferenceNameElement();

      if (results.length > 0) {
        String message = GroovyBundle.message("ambiguous.constructor.call");
        myHolder.createWarningAnnotation(toHighlight, message);
      }
      else {
        if (element instanceof PsiClass) {
          //default constructor invocation
          PsiType[] argumentTypes = PsiUtil.getArgumentTypes(refElement, true);
          if (argumentTypes == null ||
              argumentTypes.length == 0 ||
              (argumentTypes.length == 1 &&
               InheritanceUtil.isInheritor(argumentTypes[0], CommonClassNames.JAVA_UTIL_MAP))) {
            checkDefaultMapConstructor(myHolder, argList, element);
          }
          else {
            String message = GroovyBundle.message("cannot.find.default.constructor", ((PsiClass)element).getName());
            myHolder.createWarningAnnotation(toHighlight, message);
          }
        }
      }
    }
  }

  @Override
  public void visitDocMethodReference(GrDocMethodReference reference) {
    checkGrDocMemberReference(reference, myHolder);
  }

  @Override
  public void visitDocFieldReference(GrDocFieldReference reference) {
    checkGrDocMemberReference(reference, myHolder);
  }

  @Override
  public void visitConstructorInvocation(GrConstructorInvocation invocation) {
    final GroovyResolveResult resolveResult = invocation.resolveConstructorGenerics();
    if (resolveResult.getElement() != null) {
      checkMethodApplicability(resolveResult, invocation, myHolder);
    }
    else {
      final GroovyResolveResult[] results = invocation.multiResolveConstructor();
      final GrArgumentList argList = invocation.getArgumentList();
      if (results.length > 0) {
        String message = GroovyBundle.message("ambiguous.constructor.call");
        myHolder.createWarningAnnotation(argList, message);
      }
      else {
        final PsiClass clazz = invocation.getDelegatedClass();
        if (clazz != null) {
          //default constructor invocation
          PsiType[] argumentTypes = PsiUtil.getArgumentTypes(invocation.getThisOrSuperKeyword(), true);
          if (argumentTypes != null && argumentTypes.length > 0) {
            String message = GroovyBundle.message("cannot.find.default.constructor", clazz.getName());
            myHolder.createWarningAnnotation(argList, message);
          }
        }
      }
    }
  }

  @Override
  public void visitBreakStatement(GrBreakStatement breakStatement) {
    checkFlowInterruptStatement(breakStatement, myHolder);
  }

  @Override
  public void visitContinueStatement(GrContinueStatement continueStatement) {
    checkFlowInterruptStatement(continueStatement, myHolder);
  }

  @Override
  public void visitLabeledStatement(GrLabeledStatement labeledStatement) {
    final String name = labeledStatement.getLabelName();
    if (ResolveUtil.resolveLabeledStatement(name, labeledStatement, true) != null) {
      myHolder.createWarningAnnotation(labeledStatement.getLabel(), GroovyBundle.message("label.already.used", name));
    }
  }

  @Override
  public void visitPackageDefinition(GrPackageDefinition packageDefinition) {
    //todo: if reference isn't resolved it construct package definition
    final PsiFile file = packageDefinition.getContainingFile();
    assert file != null;

    PsiDirectory psiDirectory = file.getContainingDirectory();
    if (psiDirectory != null) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
      if (aPackage != null) {
        String packageName = aPackage.getQualifiedName();
        if (!packageDefinition.getPackageName().equals(packageName)) {
          final Annotation annotation = myHolder.createWarningAnnotation(packageDefinition, "wrong package name");
          annotation.registerFix(new ChangePackageQuickFix((GroovyFile)packageDefinition.getContainingFile(), packageName));
        }
      }
    }
    final GrModifierList modifierList = packageDefinition.getAnnotationList();
    checkAnnotationList(myHolder, modifierList, GroovyBundle.message("package.definition.cannot.have.modifiers"));
  }

  @Override
  public void visitClosure(GrClosableBlock closure) {
    super.visitClosure(closure);
    if (!closure.hasParametersSection()) {
      final PsiElement parent = closure.getParent();
      if (parent instanceof GrCodeBlock || parent instanceof GroovyFile) {
        myHolder.createErrorAnnotation(closure, GroovyBundle.message("ambiguous.code.block"));
      }
    }
  }

  @Override
  public void visitSuperExpression(GrSuperReferenceExpression superExpression) {
    checkThisOrSuperReferenceExpression(superExpression, myHolder);
  }

  @Override
  public void visitThisExpression(GrThisReferenceExpression thisExpression) {
    checkThisOrSuperReferenceExpression(thisExpression, myHolder);
  }

  @Override
  public void visitLiteralExpression(GrLiteral literal) {
    String text = literal.getText();
    if (text.startsWith("'''")) {
      if (text.length() < 6 || !text.endsWith("'''")) {
        myHolder.createErrorAnnotation(literal, GroovyBundle.message("string.end.expected"));
      }
    }
    else if (text.startsWith("'")) {
      if (text.length() < 2 || !text.endsWith("'")) {
        myHolder.createErrorAnnotation(literal, GroovyBundle.message("string.end.expected"));
      }
    }
  }

  @Override
  public void visitForInClause(GrForInClause forInClause) {
    final GrVariable[] declaredVariables = forInClause.getDeclaredVariables();
    if (declaredVariables.length < 1) return;
    final GrVariable variable = declaredVariables[0];
    final GrModifierList modifierList = ((GrModifierList)variable.getModifierList());
    if (modifierList == null) return;
    final PsiElement[] modifiers = modifierList.getModifiers();
    for (PsiElement modifier : modifiers) {
      if (modifier instanceof PsiAnnotation) continue;
      final String modifierText = modifier.getText();
      if (GrModifier.FINAL.equals(modifierText)) continue;
      if (GrModifier.DEF.equals(modifierText)) continue;
      myHolder.createErrorAnnotation(modifier, GroovyBundle.message("not.allowed.modifier.in.forin", modifierText));
    }
  }

  @Override
  public void visitFile(GroovyFileBase file) {
    if (!file.isScript()) return;

    List<GrMethod> methods = new ArrayList<GrMethod>();

    for (GrTopLevelDefintion topLevelDefinition : file.getTopLevelDefinitions()) {
      if (topLevelDefinition instanceof GrMethod) {
        methods.add(((GrMethod)topLevelDefinition));
      }
    }

    checkDuplicateMethod(methods.toArray(new GrMethod[methods.size()]), myHolder);
  }

  @Override
  public void visitImportStatement(GrImportStatement importStatement) {
    checkAnnotationList(myHolder, importStatement.getAnnotationList(), GroovyBundle.message("import.statement.cannot.have.modifiers"));
  }

  private static void checkFlowInterruptStatement(GrFlowInterruptingStatement statement, AnnotationHolder holder) {
    final PsiElement label = statement.getLabelIdentifier();

    if (label != null) {
      final GrLabeledStatement resolved = statement.resolveLabel();
      if (resolved == null) {
        holder.createErrorAnnotation(label, GroovyBundle.message("undefined.label", statement.getLabelName()));
      }
    }

    final GrStatement targetStatement = statement.findTargetStatement();
    if (targetStatement == null) {
      if (statement instanceof GrContinueStatement && label == null) {
        holder.createErrorAnnotation(statement, GroovyBundle.message("continue.outside.loop"));
      }
      else if (statement instanceof GrBreakStatement && label == null) {
        holder.createErrorAnnotation(statement, GroovyBundle.message("break.outside.loop.or.switch"));
      }
    }
    if (statement instanceof GrBreakStatement && label != null && findFirstLoop(statement) == null) {
      holder.createErrorAnnotation(statement, GroovyBundle.message("break.outside.loop"));
    }
  }

  @Nullable
  private static GrLoopStatement findFirstLoop(GrFlowInterruptingStatement statement) {
    return PsiTreeUtil.getParentOfType(statement, GrLoopStatement.class, true, GrClosableBlock.class, GrMember.class, GroovyFile.class);
  }

  private static void checkThisOrSuperReferenceExpression(GrExpression expression, AnnotationHolder holder) {
    final GrReferenceExpression qualifier = expression instanceof GrThisReferenceExpression
                                            ? ((GrThisReferenceExpression)expression).getQualifier()
                                            : ((GrSuperReferenceExpression)expression).getQualifier();
    if (qualifier == null) {
      if (expression instanceof GrSuperReferenceExpression) { //'this' refers to java.lang.Class<ThisClass> in static context
        final GrMethod method = PsiTreeUtil.getParentOfType(expression, GrMethod.class);
        if (method != null && method.hasModifierProperty(GrModifier.STATIC)) {
          holder.createErrorAnnotation(expression, GroovyBundle.message("cannot.reference.nonstatic", expression.getText()));
        }
      }
    }
    else {
      final PsiElement resolved = qualifier.resolve();
      if (resolved instanceof PsiClass) {
        if (PsiTreeUtil.isAncestor(resolved, expression, true)) {
          if (!PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, expression, true)) {
            holder.createErrorAnnotation(expression, GroovyBundle.message("cannot.reference.nonstatic", expression.getText()));
          }
        }
        else {
          holder.createErrorAnnotation(expression, GroovyBundle.message("is.not.enclosing.class", ((PsiClass)resolved).getQualifiedName()));
        }
      }
      else {
        holder.createErrorAnnotation(qualifier, GroovyBundle.message("unknown.class", qualifier.getText()));
      }
    }
  }

  private static void checkStaticDeclarationsInInnerClass(GrMember classMember, AnnotationHolder holder) {
    final PsiClass containingClass = classMember.getContainingClass();
    if (containingClass == null) return;
    if (com.intellij.psi.util.PsiUtil.isInnerClass(containingClass)) {
      if (classMember.hasModifierProperty(GrModifier.STATIC)) {
        final PsiElement modifier = findModifierStatic(classMember);
        if (modifier != null) {
          final Annotation annotation = holder.createErrorAnnotation(modifier, GroovyBundle.message("cannot.have.static.declarations"));
          //noinspection ConstantConditions
          annotation.registerFix(new GrModifierFix(classMember, classMember.getModifierList(), GrModifier.STATIC, true, false));
        }
      }
    }
  }

  @Nullable
  private static PsiElement findModifierStatic(GrMember grMember) {
    final GrModifierList list = grMember.getModifierList();
    if (list == null) {
      return null;
    }

    for (PsiElement modifier : list.getModifiers()) {
      if (GrModifier.STATIC.equals(modifier.getText())) {
        return modifier;
      }
    }
    return null;
  }

  private static void checkGrDocReferenceElement(AnnotationHolder holder, PsiElement element) {
    ASTNode node = element.getNode();
    if (node != null && TokenSets.BUILT_IN_TYPE.contains(node.getElementType())) {
      Annotation annotation = holder.createInfoAnnotation(element, null);
      annotation.setTextAttributes(DefaultHighlighter.KEYWORD);
    }
  }

  private static void checkAnnotationList(AnnotationHolder holder, @Nullable GrModifierList modifierList, String message) {
    if (modifierList == null) return;
    final PsiElement[] modifiers = modifierList.getModifiers();
    for (PsiElement modifier : modifiers) {
      if (!(modifier instanceof PsiAnnotation)) {
        holder.createErrorAnnotation(modifier, message);
      }
    }
  }

  private static void checkImplementedMethodsOfClass(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.hasModifierProperty(GrModifier.ABSTRACT)) return;
    if (typeDefinition.isEnum() || typeDefinition.isAnnotationType()) return;
    if (typeDefinition instanceof GrTypeParameter) return;

    Collection<CandidateInfo> collection = GroovyOverrideImplementUtil.getMethodsToImplement(typeDefinition);
    if (collection.isEmpty()) return;

    final PsiElement element = collection.iterator().next().getElement();
    assert element instanceof PsiNamedElement;
    String notImplementedMethodName = ((PsiNamedElement)element).getName();

    final TextRange range = getHeaderTextRange(typeDefinition);
    final Annotation annotation = holder.createErrorAnnotation(range,
                                                               GroovyBundle.message("method.is.not.implemented", notImplementedMethodName));
    registerImplementsMethodsFix(typeDefinition, annotation);
  }

  private static TextRange getHeaderTextRange(GrNamedElement namedElement) {
    final int startOffset = namedElement.getTextOffset();
    int endOffset = namedElement.getNameIdentifierGroovy().getTextRange().getEndOffset();
    return new TextRange(startOffset, endOffset);
  }

  private static TextRange getMethodHeaderTextRange(GrMethod method) {
    final int startOffset = method.getTextOffset();
    final ASTNode node = method.getNode().findChildByType(GroovyTokenTypes.mRPAREN);
    assert node != null;
    int endOffset = node.getTextRange().getEndOffset();
    return new TextRange(startOffset, endOffset);
  }

  private static void registerImplementsMethodsFix(GrTypeDefinition typeDefinition, Annotation annotation) {
    annotation.registerFix(new ImplementMethodsQuickFix(typeDefinition));
  }

  private static void checkInnerMethod(AnnotationHolder holder, GrMethod grMethod) {
    final PsiElement parent = grMethod.getParent();
    if (parent instanceof GrOpenBlock || parent instanceof GrClosableBlock) {
      holder.createErrorAnnotation(grMethod.getNameIdentifierGroovy(), GroovyBundle.message("Inner.methods.are.not.supported"));
    }
  }

  private static void registerAbstractMethodFix(Annotation annotation, GrMethod method, boolean makeClassAbstract) {
    if (method.getBlock() == null) {
      annotation.registerFix(new AddMethodBodyFix(method));
    }
    else {
      annotation.registerFix(new GrModifierFix(method, method.getModifierList(), GrModifier.ABSTRACT, false, false));
    }
    if (makeClassAbstract) {
      final PsiClass containingClass = method.getContainingClass();
      LOG.assertTrue(containingClass != null);
      final GrModifierList list = (GrModifierList)containingClass.getModifierList();
      LOG.assertTrue(list != null);
      annotation.registerFix(new GrModifierFix(containingClass, list, GrModifier.ABSTRACT, false, true));
    }
  }

  private static void checkMethodDefinitionModifiers(AnnotationHolder holder, GrMethod method) {
    final GrModifierList modifiersList = method.getModifierList();
    checkAccessModifiers(holder, modifiersList, method);
    checkDuplicateModifiers(holder, modifiersList, method);
    checkOverrideAnnotation(holder, modifiersList, method);

    //script methods
    boolean isMethodAbstract = modifiersList.hasExplicitModifier(GrModifier.ABSTRACT);
    final boolean isMethodStatic = modifiersList.hasExplicitModifier(GrModifier.STATIC);
    if (method.getParent() instanceof GroovyFileBase) {
      if (isMethodAbstract) {
        final Annotation annotation =
          holder.createErrorAnnotation(modifiersList, GroovyBundle.message("script.cannot.have.modifier.abstract"));
        registerAbstractMethodFix(annotation, method, false);
      }

      if (modifiersList.hasExplicitModifier(GrModifier.NATIVE)) {
        final Annotation annotation =
          holder.createErrorAnnotation(modifiersList, GroovyBundle.message("script.cannot.have.modifier.native"));
        annotation.registerFix(new GrModifierFix(method, modifiersList, GrModifier.NATIVE, false, false));
      }
    }
    else  //type definition methods
      if (method.getParent() != null && method.getParent().getParent() instanceof GrTypeDefinition) {
        GrTypeDefinition containingTypeDef = ((GrTypeDefinition)method.getParent().getParent());

        //interface
        if (containingTypeDef.isInterface()) {
          if (isMethodStatic) {
            final Annotation annotation =
              holder.createErrorAnnotation(modifiersList, GroovyBundle.message("interface.must.have.no.static.method"));
            annotation.registerFix(new GrModifierFix(method, modifiersList, GrModifier.STATIC, true, false));
          }

          if (modifiersList.hasExplicitModifier(GrModifier.PRIVATE)) {
            final Annotation annotation =
              holder.createErrorAnnotation(modifiersList, GroovyBundle.message("interface.must.have.no.private.method"));
            annotation.registerFix(new GrModifierFix(method, modifiersList, GrModifier.PRIVATE, true, false));
          }

        }
        else if (containingTypeDef.isEnum()) {
          //enumeration
          //todo
        }
        else if (containingTypeDef.isAnnotationType()) {
          //annotation
          //todo
        }
        else if (containingTypeDef.isAnonymous()) {
          //anonymous class
          if (isMethodStatic) {
            final Annotation annotation =
              holder.createErrorAnnotation(modifiersList, GroovyBundle.message("static.declaration.in.inner.class"));
            annotation.registerFix(new GrModifierFix(method, modifiersList, GrModifier.STATIC, false, false));
          }
          if (method.isConstructor()) {
            holder.createErrorAnnotation(method.getNameIdentifierGroovy(),
                                         GroovyBundle.message("constructors.are.not.allowed.in.anonymous.class"));
          }
          if (isMethodAbstract) {
            final Annotation annotation =
              holder.createErrorAnnotation(modifiersList, GroovyBundle.message("anonymous.class.cannot.have.abstract.method"));
            registerAbstractMethodFix(annotation, method, false);
          }
        }
        else {
          //class
          PsiModifierList typeDefModifiersList = containingTypeDef.getModifierList();
          LOG.assertTrue(typeDefModifiersList != null, "modifiers list must be not null");

          if (!typeDefModifiersList.hasExplicitModifier(GrModifier.ABSTRACT)) {
            if (isMethodAbstract) {
              final Annotation annotation =
                holder.createErrorAnnotation(modifiersList, GroovyBundle.message("only.abstract.class.can.have.abstract.method"));
              registerAbstractMethodFix(annotation, method, true);
            }
          }

          if (!isMethodAbstract) {
            if (method.getBlock() == null) {
              final Annotation annotation = holder
                .createErrorAnnotation(method.getNameIdentifierGroovy(), GroovyBundle.message("not.abstract.method.should.have.body"));
              annotation.registerFix(new AddMethodBodyFix(method));

            }
          }
          if (isMethodStatic) {
            checkStaticDeclarationsInInnerClass(method, holder);
          }
        }
      }
  }

  private static void checkOverrideAnnotation(AnnotationHolder holder, GrModifierList list, GrMethod method) {
    final PsiAnnotation overrideAnnotation = list.findAnnotation("java.lang.Override");
    if (overrideAnnotation == null) {
      return;
    }
    try {
      MethodSignatureBackedByPsiMethod superMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superMethod == null) {
        holder.createWarningAnnotation(overrideAnnotation, GroovyBundle.message("method.doesnot.override.super"));
      }

    }
    catch (IndexNotReadyException e) {
    }
  }

  private static void checkTypeDefinitionModifiers(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    GrModifierList modifiersList = typeDefinition.getModifierList();

    if (modifiersList == null) return;

    /**** class ****/
    checkAccessModifiers(holder, modifiersList, typeDefinition);
    checkDuplicateModifiers(holder, modifiersList, typeDefinition);

    PsiClassType[] extendsListTypes = typeDefinition.getExtendsListTypes();

    for (PsiClassType classType : extendsListTypes) {
      PsiClass psiClass = classType.resolve();

      if (psiClass != null) {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList != null) {
          if (modifierList.hasExplicitModifier(GrModifier.FINAL)) {
            final Annotation annotation = holder
              .createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), GroovyBundle.message("final.class.cannot.be.extended"));
            annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, GrModifier.FINAL, false, false));
          }
        }
      }
    }

    if (modifiersList.hasExplicitModifier(GrModifier.ABSTRACT) && modifiersList.hasExplicitModifier(GrModifier.FINAL)) {
      final Annotation annotation =
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.abstract.and.final"));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, GrModifier.FINAL, false, false));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, GrModifier.ABSTRACT, false, false));

    }

    if (modifiersList.hasExplicitModifier(GrModifier.TRANSIENT)) {
      final Annotation annotation =
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("modifier.transient.not.allowed.here"));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, GrModifier.TRANSIENT, false, false));
    }
    if (modifiersList.hasExplicitModifier(GrModifier.VOLATILE)) {
      final Annotation annotation = holder.createErrorAnnotation(modifiersList, GroovyBundle.message("modifier.volatile.not.allowed.here"));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, GrModifier.VOLATILE, false, false));
    }

    /**** interface ****/
    if (typeDefinition.isInterface()) {
      if (modifiersList.hasExplicitModifier(GrModifier.FINAL)) {
        final Annotation annotation =
          holder.createErrorAnnotation(modifiersList, GroovyBundle.message("intarface.cannot.have.modifier.final"));
        annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, GrModifier.FINAL, false, false));
      }
    }

    checkStaticDeclarationsInInnerClass(typeDefinition, holder);
  }

  private static void checkDuplicateModifiers(AnnotationHolder holder, @NotNull GrModifierList list, PsiMember member) {
    final PsiElement[] modifiers = list.getModifiers();
    Set<String> set = new THashSet<String>(modifiers.length);
    for (PsiElement modifier : modifiers) {
      String name = modifier.getText();
      if (set.contains(name)) {
        final Annotation annotation = holder.createErrorAnnotation(list, GroovyBundle.message("duplicate.modifier", name));
        annotation.registerFix(new GrModifierFix(member, list, name, false, false));
      }
      else {
        set.add(name);
      }
    }
  }

  private static void checkAccessModifiers(AnnotationHolder holder, @NotNull PsiModifierList modifierList, PsiMember member) {
    boolean hasPrivate = modifierList.hasExplicitModifier(GrModifier.PRIVATE);
    boolean hasPublic = modifierList.hasExplicitModifier(GrModifier.PUBLIC);
    boolean hasProtected = modifierList.hasExplicitModifier(GrModifier.PROTECTED);

    if (hasPrivate && hasPublic || hasPrivate && hasProtected || hasPublic && hasProtected) {
      final Annotation annotation = holder.createErrorAnnotation(modifierList, GroovyBundle.message("illegal.combination.of.modifiers"));
      if (hasPrivate) {
        annotation.registerFix(new GrModifierFix(member, modifierList, GrModifier.PRIVATE, false, false));
      }
      if (hasProtected) {
        annotation.registerFix(new GrModifierFix(member, modifierList, GrModifier.PROTECTED, false, false));
      }
      if (hasPublic) {
        annotation.registerFix(new GrModifierFix(member, modifierList, GrModifier.PUBLIC, false, false));
      }
    }
  }

  private static void checkDuplicateMethod(GrMethod[] methods, AnnotationHolder holder) {
    MultiMap<MethodSignature, PsiMethod> map = GrClosureSignatureUtil.findMethodSignatures(methods);
    processMethodDuplicates(map, holder);
  }

  protected static void processMethodDuplicates(MultiMap<MethodSignature, PsiMethod> map, AnnotationHolder holder) {
    for (MethodSignature signature : map.keySet()) {
      Collection<PsiMethod> methods = map.get(signature);
      if (methods.size() > 1) {
        String signaturePresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
        for (PsiMethod method : methods) {
          holder.createErrorAnnotation(method.getNameIdentifier(), GroovyBundle.message("method.duplicate", signaturePresentation, method.getContainingClass().getName()));
        }
      }
    }
  }

  private static void checkTypeDefinition(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    final GroovyConfigUtils configUtils = GroovyConfigUtils.getInstance();
    if (typeDefinition.isAnnotationType()) {
      Annotation annotation = holder.createInfoAnnotation(typeDefinition.getNameIdentifierGroovy(), null);
      annotation.setTextAttributes(DefaultHighlighter.ANNOTATION);
    }
    else if (typeDefinition.isAnonymous()) {
      if (!configUtils.isAtLeastGroovy1_7(typeDefinition)) {
        holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(),
                                     GroovyBundle.message("anonymous.classes.are.not.supported", configUtils.getSDKVersion(typeDefinition)));
      }
    }
    else if (typeDefinition.getContainingClass() != null && !(typeDefinition instanceof GrEnumTypeDefinition)) {
      if (!configUtils.isAtLeastGroovy1_7(typeDefinition)) {
        holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(),
                                     GroovyBundle.message("inner.classes.are.not.supported", configUtils.getSDKVersion(typeDefinition)));
      }
    }

    final GrImplementsClause implementsClause = typeDefinition.getImplementsClause();
    final GrExtendsClause extendsClause = typeDefinition.getExtendsClause();

    if (implementsClause != null) {
      checkForImplementingClass(holder, extendsClause, implementsClause, ((GrTypeDefinition)implementsClause.getParent()));
    }

    if (extendsClause != null) {
      checkForExtendingInterface(holder, extendsClause, implementsClause, ((GrTypeDefinition)extendsClause.getParent()));
    }

    checkForWildCards(holder, extendsClause);
    checkForWildCards(holder, implementsClause);

    checkDuplicateClass(typeDefinition, holder);

    checkCyclicInheritance(holder, typeDefinition);
  }

  private static void checkCyclicInheritance(AnnotationHolder holder,
                                             GrTypeDefinition typeDefinition) {
    final PsiClass psiClass = getCircularClass(typeDefinition, new HashSet<PsiClass>());
    if (psiClass != null) {
      holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(),
                                   GroovyBundle.message("cyclic.inheritance.involving.0", psiClass.getQualifiedName()));
    }
  }

  @Nullable
  private static PsiClass getCircularClass(PsiClass aClass, Collection<PsiClass> usedClasses) {
    if (usedClasses.contains(aClass)) {
      return aClass;
    }
    try {
      usedClasses.add(aClass);
      PsiClass[] superTypes = aClass.getSupers();
      for (PsiElement superType : superTypes) {
        while (superType instanceof PsiClass) {
          if (!"java.lang.Object".equals(((PsiClass)superType).getQualifiedName())) {
            PsiClass circularClass = getCircularClass((PsiClass)superType, usedClasses);
            if (circularClass != null) return circularClass;
          }
          // check class qualifier
          superType = superType.getParent();
        }
      }
    }
    finally {
      usedClasses.remove(aClass);
    }
    return null;
  }

  private static void checkForWildCards(AnnotationHolder holder, @Nullable GrReferenceList clause) {
    if (clause == null) return;
    final GrCodeReferenceElement[] elements = clause.getReferenceElements();
    for (GrCodeReferenceElement element : elements) {
      final GrTypeArgumentList list = element.getTypeArgumentList();
      if (list != null) {
        for (GrTypeElement type : list.getTypeArgumentElements()) {
          if (type instanceof GrWildcardTypeArgument) {
            holder.createErrorAnnotation(type, GroovyBundle.message("wildcards.are.not.allowed.in.extends.list"));
          }
        }
      }
    }
  }

  private static void checkDuplicateClass(GrTypeDefinition typeDefinition, AnnotationHolder holder) {
    final PsiClass containingClass = typeDefinition.getContainingClass();
    if (containingClass != null) {
      final String containingClassName = containingClass.getName();
      if (containingClassName != null && containingClassName.equals(typeDefinition.getName())) {
        holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(),
                                     GroovyBundle.message("duplicate.inner.class", typeDefinition.getName()));
      }
    }
    final String qName = typeDefinition.getQualifiedName();
    if (qName != null) {
      final PsiClass[] classes =
        JavaPsiFacade.getInstance(typeDefinition.getProject()).findClasses(qName, typeDefinition.getResolveScope());
      if (classes.length > 1) {
        String packageName = getPackageName(typeDefinition);

        if (!isScriptGeneratedClass(classes)) {
          holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(),
                                       GroovyBundle.message("duplicate.class", typeDefinition.getName(), packageName));
        }
        else {
          holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(),
                                       GroovyBundle.message("script.generated.with.same.name", qName));
        }
      }
    }
  }

  private static String getPackageName(GrTypeDefinition typeDefinition) {
    final PsiFile file = typeDefinition.getContainingFile();
    String packageName = "<default package>";
    if (file instanceof GroovyFile) {
      final String name = ((GroovyFile)file).getPackageName();
      if (name.length() > 0) packageName = name;
    }
    return packageName;
  }

  private static boolean isScriptGeneratedClass(PsiClass[] allClasses) {
    return allClasses.length == 2 && (allClasses[0] instanceof GroovyScriptClass || allClasses[1] instanceof GroovyScriptClass);
  }

  private static void checkForExtendingInterface(AnnotationHolder holder,
                                          GrExtendsClause extendsClause,
                                          GrImplementsClause implementsClause,
                                          GrTypeDefinition myClass) {
    for (GrCodeReferenceElement ref : extendsClause.getReferenceElements()) {
      final PsiElement clazz = ref.resolve();
      if (clazz == null) continue;

      if (myClass.isInterface() && clazz instanceof PsiClass && !((PsiClass)clazz).isInterface()) {
        final Annotation annotation = holder.createErrorAnnotation(ref, GroovyBundle.message("class.is.not.expected.here"));
        annotation.registerFix(new ChangeExtendsImplementsQuickFix(extendsClause, implementsClause));
      }
    }
  }

  private static void checkForImplementingClass(AnnotationHolder holder,
                                         GrExtendsClause extendsClause,
                                         GrImplementsClause implementsClause,
                                         GrTypeDefinition myClass) {
    if (myClass.isInterface()) {
      final Annotation annotation =
        holder.createErrorAnnotation(implementsClause, GroovyBundle.message("interface.cannot.contain.implements.clause"));
      annotation.registerFix(new ChangeExtendsImplementsQuickFix(extendsClause, implementsClause));
      return;
    }

    for (GrCodeReferenceElement ref : implementsClause.getReferenceElements()) {
      final PsiElement clazz = ref.resolve();
      if (clazz == null) continue;

      if (!((PsiClass)clazz).isInterface()) {
        final Annotation annotation = holder.createErrorAnnotation(ref, GroovyBundle.message("interface.expected.here"));
        annotation.registerFix(new ChangeExtendsImplementsQuickFix(extendsClause, implementsClause));
      }
    }
  }

  private static void checkGrDocMemberReference(final GrDocMemberReference reference, AnnotationHolder holder) {
    PsiElement resolved = reference.resolve();
    if (resolved == null) {
      Annotation annotation = holder.createErrorAnnotation(reference, GroovyBundle.message("cannot.resolve", reference.getReferenceName()));
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
    }
  }

  private static void registerReferenceFixes(GrReferenceExpression refExpr, Annotation annotation) {
    PsiClass targetClass = QuickfixUtil.findTargetClass(refExpr);
    if (targetClass == null) return;

    addDynamicAnnotation(annotation, refExpr);
    if (targetClass instanceof GrMemberOwner) {
      if (!(targetClass instanceof GroovyScriptClass)) {
        annotation.registerFix(new CreateFieldFromUsageFix(refExpr, (GrMemberOwner)targetClass));
      }

      if (refExpr.getParent() instanceof GrCall && refExpr.getParent() instanceof GrExpression) {
        annotation.registerFix(new CreateMethodFromUsageFix(refExpr, (GrMemberOwner)targetClass));
      }
    }

    if (!refExpr.isQualified()) {
      GrVariableDeclarationOwner owner = PsiTreeUtil.getParentOfType(refExpr, GrVariableDeclarationOwner.class);
      if (!(owner instanceof GroovyFileBase) || ((GroovyFileBase)owner).isScript()) {
        annotation.registerFix(new CreateLocalVariableFromUsageFix(refExpr, owner));
      }
    }
  }

  private static void addDynamicAnnotation(Annotation annotation, GrReferenceExpression referenceExpression) {
    final PsiFile containingFile = referenceExpression.getContainingFile();
    VirtualFile file;
    if (containingFile != null) {
      file = containingFile.getVirtualFile();
      if (file == null) return;
    }
    else {
      return;
    }

    if (QuickfixUtil.isCall(referenceExpression)) {
      annotation.registerFix(new DynamicMethodFix(referenceExpression), referenceExpression.getTextRange());
    }
    else {
      annotation.registerFix(new DynamicPropertyFix(referenceExpression), referenceExpression.getTextRange());
    }
  }

  private static void highlightMemberResolved(AnnotationHolder holder, GrReferenceExpression refExpr, PsiMember member) {
    boolean isStatic = member.hasModifierProperty(GrModifier.STATIC);
    Annotation annotation = holder.createInfoAnnotation(refExpr.getReferenceNameElement(), null);

    if (member instanceof PsiField || member instanceof GrAccessorMethod) {
      annotation.setTextAttributes(isStatic ? DefaultHighlighter.STATIC_FIELD : DefaultHighlighter.INSTANCE_FIELD);
      return;
    }
    if (member instanceof PsiMethod) {
      annotation.setTextAttributes(!isStatic ? DefaultHighlighter.METHOD_CALL : DefaultHighlighter.STATIC_METHOD_ACCESS);
    }
  }


  private static void registerUsedImport(GrReferenceElement referenceElement, GroovyResolveResult resolveResult) {
    GroovyPsiElement context = resolveResult.getCurrentFileResolveContext();
    if (context instanceof GrImportStatement) {
      PsiFile file = referenceElement.getContainingFile();
      if (file instanceof GroovyFile) {
        GroovyImportsTracker importsTracker = GroovyImportsTracker.getInstance(referenceElement.getProject());
        importsTracker.registerImportUsed((GrImportStatement)context);
      }
    }
  }

  private static void checkMethodApplicability(GroovyResolveResult methodResolveResult, GroovyPsiElement place, AnnotationHolder holder) {
    final PsiElement element = methodResolveResult.getElement();
    if (!(element instanceof PsiMethod)) return;

    final PsiMethod method = (PsiMethod)element;
    PsiType[] argumentTypes = PsiUtil.getArgumentTypes(place, true);
    if ("call".equals(method.getName()) && place instanceof GrReferenceExpression) {
      final GrExpression qualifierExpression = ((GrReferenceExpression)place).getQualifierExpression();
      if (qualifierExpression != null) {
        final PsiType type = qualifierExpression.getType();
        if (type instanceof GrClosureType) {
          if (!PsiUtil.isApplicable(argumentTypes, (GrClosureType)type, place)) {
            highlightInapplicableMethodUsage(methodResolveResult, place, holder, method, argumentTypes);
            return;
          }
        }
      }
    }
    if (argumentTypes != null &&
             !PsiUtil.isApplicable(argumentTypes, method, methodResolveResult.getSubstitutor(),
                                   methodResolveResult.getCurrentFileResolveContext() instanceof GrMethodCallExpression, place)) {
      
      //check for implicit use of property getter which returns closure
      if (GroovyPropertyUtils.isSimplePropertyGetter(method)) {
        if (method instanceof GrMethod || method instanceof GrAccessorMethod) {
          final PsiType returnType = PsiUtil.getSmartReturnType(method);
          if (returnType instanceof GrClosureType) {
            if (PsiUtil.isApplicable(argumentTypes, ((GrClosureType)returnType), place)) {
              return;
            }
          }
        }

        PsiType returnType = method.getReturnType();
        if (returnType != null) {
          final PsiClassType closureType = JavaPsiFacade.getElementFactory(element.getProject())
            .createTypeByFQClassName(GrClosableBlock.GROOVY_LANG_CLOSURE, GlobalSearchScope.allScope(element.getProject()));
          if (TypesUtil.isAssignable(closureType, returnType, place)) {
            return;
          }
        }
      }

      highlightInapplicableMethodUsage(methodResolveResult, place, holder, method, argumentTypes);
    }
  }

  private static void highlightInapplicableMethodUsage(GroovyResolveResult methodResolveResult, PsiElement place, AnnotationHolder holder,
                                                        PsiMethod method, PsiType[] argumentTypes) {
    PsiElement elementToHighlight = PsiUtil.getArgumentsList(place);
    if (elementToHighlight == null) {
      elementToHighlight = place;
    }

    final String typesString = buildArgTypesList(argumentTypes);
    String message;
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass != null) {
      final PsiClassType containingType = JavaPsiFacade.getInstance(method.getProject()).getElementFactory()
        .createType(containingClass, methodResolveResult.getSubstitutor());
      message = GroovyBundle.message("cannot.apply.method1", method.getName(), containingType.getInternalCanonicalText(), typesString);
    }
    else {
      message = GroovyBundle.message("cannot.apply.method.or.closure", method.getName(), typesString);
    }
    holder.createWarningAnnotation(elementToHighlight, message);
  }

  public static boolean isDeclarationAssignment(GrReferenceExpression refExpr) {
    if (isAssignmentLhs(refExpr)) {
      return isExpandoQualified(refExpr);
    }
    return false;
  }

  private static boolean isAssignmentLhs(GrReferenceExpression refExpr) {
    return refExpr.getParent() instanceof GrAssignmentExpression &&
        refExpr.equals(((GrAssignmentExpression)refExpr.getParent()).getLValue());
  }

  private static boolean isExpandoQualified(GrReferenceExpression refExpr) {
    final GrExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null) {
      final PsiClass clazz = PsiTreeUtil.getParentOfType(refExpr, PsiClass.class);
      if (clazz == null) { //script
        return true;
      }
      return false; //in class, a property should normally be defined, so it's not a declaration
    }

    final PsiType type = qualifier.getType();
    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass psiClass = classType.resolve();
      if (psiClass instanceof GroovyScriptClass) {
        return true;
      }
    }
    return false;
  }

  private static void checkSingleResolvedElement(AnnotationHolder holder, GrReferenceElement refElement, GroovyResolveResult resolveResult, boolean highlightError) {
    final PsiElement resolved = resolveResult.getElement();
    if (resolved == null) {
      String message = GroovyBundle.message("cannot.resolve", refElement.getReferenceName());

      // Register quickfix
      final PsiElement nameElement = refElement.getReferenceNameElement();
      final PsiElement toHighlight = nameElement != null ? nameElement : refElement;

      final Annotation annotation;
      if (highlightError) {
        annotation = holder.createErrorAnnotation(toHighlight, message);
        annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
      else {
        annotation = holder.createInfoAnnotation(toHighlight, message);
      }
      // todo implement for nested classes
      if (refElement.getQualifier() == null) {
        registerCreateClassByTypeFix(refElement, annotation);
        registerAddImportFixes(refElement, annotation);
      }
    }
    else if (!resolveResult.isAccessible()) {
      String message = GroovyBundle.message("cannot.access", refElement.getReferenceName());
      holder.createWarningAnnotation(refElement.getReferenceNameElement(), message);
    }
  }

  private static void checkDefaultMapConstructor(AnnotationHolder holder, GrArgumentList argList, PsiElement element) {
    if (argList != null) {
      final GrNamedArgument[] args = argList.getNamedArguments();
      for (GrNamedArgument arg : args) {
        final GrArgumentLabel label = arg.getLabel();
        if (label == null) continue;
        if (label.getName() == null) {
          final PsiElement nameElement = label.getNameElement();
          if (nameElement instanceof GrExpression) {
            final PsiType stringType =
              JavaPsiFacade.getElementFactory(arg.getProject()).createTypeFromText(CommonClassNames.JAVA_LANG_STRING, arg);
            if (!TypesUtil.isAssignable(stringType, ((GrExpression)nameElement).getType(), arg)) {
              holder.createWarningAnnotation(nameElement, GroovyBundle.message("property.name.expected"));
            }
          }
          else {
            holder.createWarningAnnotation(nameElement, GroovyBundle.message("property.name.expected"));
          }
        }
        else {
          final PsiElement resolved = label.resolve();
          if (resolved == null) {
            final Annotation annotation = holder.createWarningAnnotation(label, GroovyBundle.message("no.such.property", label.getName()));

            if (element instanceof PsiMember && !(element instanceof PsiClass)) {
              element = ((PsiMember)element).getContainingClass();
            }
            if (element instanceof GrMemberOwner) {
              annotation.registerFix(new CreateFieldFromConstructorLabelFix((GrMemberOwner)element, label.getNamedArgument()));
            }
            if (element instanceof PsiClass) {
              annotation.registerFix(new DynamicPropertyFix(label, (PsiClass)element));
            }
          }
        }
      }
    }
  }

  private static void checkClosureApplicability(GroovyResolveResult resolveResult, PsiType type, GroovyPsiElement place, AnnotationHolder holder) {
    final PsiElement element = resolveResult.getElement();
    if (!(element instanceof GrVariable)) return;
    if (!(type instanceof GrClosureType)) return;
    final GrVariable variable = (GrVariable)element;
    PsiType[] argumentTypes = PsiUtil.getArgumentTypes(place, true);
    if (argumentTypes == null) return;

    if (PsiUtil.isApplicable(argumentTypes, (GrClosureType)type, place)) return;

    final String typesString = buildArgTypesList(argumentTypes);
    String message = GroovyBundle.message("cannot.apply.method.or.closure", variable.getName(), typesString);
    PsiElement elementToHighlight = PsiUtil.getArgumentsList(place);
    if (elementToHighlight == null) elementToHighlight = place;
    holder.createWarningAnnotation(elementToHighlight, message);
  }

  private static void registerAddImportFixes(GrReferenceElement refElement, Annotation annotation) {
    final String referenceName = refElement.getReferenceName();
    //noinspection ConstantConditions
    if (StringUtil.isEmpty(referenceName) || Character.isLowerCase(referenceName.charAt(0))) {
      return;
    }

    annotation.registerFix(new GroovyAddImportAction(refElement));
  }

  private static void registerCreateClassByTypeFix(GrReferenceElement refElement, Annotation annotation) {
    GrPackageDefinition packageDefinition = PsiTreeUtil.getParentOfType(refElement, GrPackageDefinition.class);
    if (packageDefinition == null && refElement.getQualifier() == null) {
      PsiElement parent = refElement.getParent();
      if (parent instanceof GrNewExpression) {
        annotation.registerFix(CreateClassFix.createClassFromNewAction((GrNewExpression)parent));
      }
      else {
        annotation.registerFix(CreateClassFix.createClassFixAction(refElement));
      }
    }
  }

  private static void highlightMember(AnnotationHolder holder, GrMember member) {
    if (member instanceof GrField) {
      GrField field = (GrField)member;
      PsiElement identifier = field.getNameIdentifierGroovy();
      final boolean isStatic = field.hasModifierProperty(GrModifier.STATIC);
      holder.createInfoAnnotation(identifier, null).setTextAttributes(isStatic ? DefaultHighlighter.STATIC_FIELD : DefaultHighlighter.INSTANCE_FIELD);
    }
  }

  private static void highlightAnnotation(AnnotationHolder holder, PsiElement refElement, GroovyResolveResult result) {
    PsiElement element = result.getElement();
    PsiElement parent = refElement.getParent();
    if (element instanceof PsiClass && ((PsiClass)element).isAnnotationType() && !(parent instanceof GrImportStatement)) {
      Annotation annotation = holder.createInfoAnnotation(parent, null);
      annotation.setTextAttributes(DefaultHighlighter.ANNOTATION);
      GroovyPsiElement context = result.getCurrentFileResolveContext();
      if (context instanceof GrImportStatement) {
        annotation = holder.createInfoAnnotation(((GrImportStatement)context).getImportReference(), null);
        annotation.setTextAttributes(DefaultHighlighter.ANNOTATION);
      }
    }

  }


  private static String buildArgTypesList(PsiType[] argTypes) {
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    for (int i = 0; i < argTypes.length; i++) {
      if (i > 0) {
        builder.append(", ");
      }
      PsiType argType = argTypes[i];
      builder.append(argType != null ? argType.getInternalCanonicalText() : "?");
    }
    builder.append(")");
    return builder.toString();
  }

  private static class DuplicateVariablesProcessor extends PropertyResolverProcessor {
    private boolean myBorderPassed;
    private final boolean myHasVisibilityModifier;

    public DuplicateVariablesProcessor(GrVariable variable) {
      super(variable.getName(), variable);
      myBorderPassed = false;
      myHasVisibilityModifier = hasExplicitVisibilityModifiers(variable);
    }

    private static boolean hasExplicitVisibilityModifiers(GrVariable variable) {
      final PsiModifierList modifierList = variable.getModifierList();
      if (modifierList instanceof GrModifierList) return ((GrModifierList)modifierList).hasExplicitVisibilityModifiers();
      if (modifierList == null) return false;
      return modifierList.hasExplicitModifier(GrModifier.PUBLIC) ||
             modifierList.hasExplicitModifier(GrModifier.PROTECTED) ||
             modifierList.hasExplicitModifier(GrModifier.PRIVATE);
    }

    @Override
    public boolean execute(PsiElement element, ResolveState state) {
      if (myBorderPassed) {
        return false;
      }
      if (element instanceof GrVariable && hasExplicitVisibilityModifiers((GrVariable)element) != myHasVisibilityModifier) {
        return true;
      }
      return super.execute(element, state);
    }

    @Override
    public void handleEvent(Event event, Object associated) {
      if (event == ResolveUtil.DECLARATION_SCOPE_PASSED) {
        myBorderPassed = true;
      }
      super.handleEvent(event, associated);
    }
  }
}

