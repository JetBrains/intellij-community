/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMethodBodyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.OrderEntryFix;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicMethodFix;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertyFix;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.extensions.GroovyUnresolvedHighlightFilter;
import org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GroovyDocPsiElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrFlowInterruptingStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.noncode.GrInheritConstructorContributor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;

import java.util.*;

/**
 * @author ven
 */
@SuppressWarnings({"unchecked"})
public class GroovyAnnotator extends GroovyElementVisitor implements Annotator {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.GroovyAnnotator");

  private AnnotationHolder myHolder;

  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof GroovyPsiElement) {
      myHolder = holder;
      ((GroovyPsiElement)element).accept(this);
      myHolder = null;
    }
  }

  @Override
  public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
    super.visitApplicationStatement(applicationStatement);
    checkForCommandExpressionSyntax(applicationStatement);
  }

  @Override
  public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
    super.visitMethodCallExpression(methodCallExpression);
    checkForCommandExpressionSyntax(methodCallExpression);
  }

  private void checkForCommandExpressionSyntax(GrMethodCall methodCall) {
    final GroovyConfigUtils groovyConfig = GroovyConfigUtils.getInstance();
    if (methodCall.isCommandExpression() && !groovyConfig.isVersionAtLeast(methodCall, GroovyConfigUtils.GROOVY1_8)) {
      myHolder
        .createErrorAnnotation(methodCall, GroovyBundle.message("is.not.supported.in.version", groovyConfig.getSDKVersion(methodCall)));
    }
  }

  @Override
  public void visitElement(GroovyPsiElement element) {
    if (element.getParent() instanceof GrDocReferenceElement) {
      checkGrDocReferenceElement(myHolder, element);
    }
  }

  @Override
  public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
    if (PsiTreeUtil.getParentOfType(refElement, GroovyDocPsiElement.class) != null) return;

    final PsiElement parent = refElement.getParent();
    GroovyResolveResult resolveResult = refElement.advancedResolve();
    highlightAnnotation(myHolder, refElement, resolveResult);
    if (refElement.getReferenceName() != null) {

      if (parent instanceof GrImportStatement && ((GrImportStatement)parent).isStatic() && refElement.multiResolve(false).length > 0) {
        return;
      }

      checkSingleResolvedElement(myHolder, refElement, resolveResult, true);

      if (resolveResult.getElement() == null) {
        final GrPackageDefinition pack = PsiTreeUtil.getParentOfType(refElement, GrPackageDefinition.class);
        if (pack != null) {
          checkPackage(pack);
        }
      }
    }
  }

  @Override
  public void visitReferenceExpression(final GrReferenceExpression referenceExpression) {
    checkStringNameIdentifier(referenceExpression);
    GroovyResolveResult resolveResult = referenceExpression.advancedResolve();
    GroovyResolveResult[] results = referenceExpression.multiResolve(false); //cached

    PsiElement resolved = resolveResult.getElement();
    final PsiElement parent = referenceExpression.getParent();
    if (resolved != null) {
      if (resolved instanceof PsiMember) {
        highlightMemberResolved(myHolder, referenceExpression, ((PsiMember)resolved));
      }
      /*if (!resolveResult.isAccessible()) {
        String message = GroovyBundle.message("cannot.access", referenceExpression.getReferenceName());
        final Annotation annotation = myHolder.createWarningAnnotation(getElementToHighlight(referenceExpression), message);
        if (resolved instanceof PsiMember) {
          registerAccessFix(annotation, referenceExpression, ((PsiMember)resolved));
        }
      }*/

      //todo uncomment when correct isStatic() is working
      if (!resolveResult.isStaticsOK() && resolved instanceof PsiModifierListOwner) {
        if (!((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) {
          Annotation annotation = myHolder.createInfoAnnotation(referenceExpression,
                                                                GroovyBundle.message("cannot.reference.nonstatic",
                                                                                     referenceExpression.getReferenceName()));
          annotation.setTextAttributes(DefaultHighlighter.UNRESOLVED_ACCESS);
        }
      }
    }
    else {
      GrExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier == null && isDeclarationAssignment(referenceExpression)) return;

      // If it is reference to map.key we shouldn't highlight key unresolved
      if (!(parent instanceof GrCall) && ResolveUtil.isKeyOfMap(referenceExpression)) {
        PsiElement refNameElement = referenceExpression.getReferenceNameElement();
        PsiElement elt = refNameElement == null ? referenceExpression : refNameElement;
        Annotation annotation = myHolder.createInfoAnnotation(elt, null);
        annotation.setTextAttributes(DefaultHighlighter.MAP_KEY);
        return;
      }


      if (parent instanceof GrReferenceExpression && "class".equals(((GrReferenceExpression)parent).getReferenceName())) {
        checkSingleResolvedElement(myHolder, referenceExpression, resolveResult, false);
      }
    }

    if (parent instanceof GrCall) {
      if (resolved == null && results.length > 0) {
        resolved = results[0].getElement();
      }
    }
    if (isDeclarationAssignment(referenceExpression) || resolved instanceof PsiPackage) return;

    if (resolved == null && shouldHighlightAsUnresolved(referenceExpression)) {
      PsiElement refNameElement = referenceExpression.getReferenceNameElement();
      PsiElement elt = refNameElement == null ? referenceExpression : refNameElement;

      Annotation annotation = myHolder.createInfoAnnotation(elt, null);
      final GrExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier == null) {
        if (parent instanceof GrMethodCall) {
          registerStaticImportFix(referenceExpression, annotation);
        }
        else {
          registerCreateClassByTypeFix(referenceExpression, annotation);
          registerAddImportFixes(referenceExpression, annotation);
        }
      }
      else {
        if (qualifier.getType() == null) {
          return;
        }
      }

      registerReferenceFixes(referenceExpression, annotation);
      UnresolvedReferenceQuickFixProvider.registerReferenceFixes(referenceExpression, new QuickFixActionRegistrarAdapter(annotation));
      OrderEntryFix.registerFixes(new QuickFixActionRegistrarAdapter(annotation), referenceExpression);

      annotation.setTextAttributes(DefaultHighlighter.UNRESOLVED_ACCESS);
    }
  }

  public static boolean shouldHighlightAsUnresolved(@NotNull GrReferenceExpression referenceExpression) {
    PsiElement refNameElement = referenceExpression.getReferenceNameElement();
    if (refNameElement != null && referenceExpression.getQualifier() == null) {
      final IElementType type = refNameElement.getNode().getElementType();
      if (TokenSets.STRING_LITERAL_SET.contains(type)) return false;
    }

    if (!GroovyUnresolvedHighlightFilter.shouldHighlight(referenceExpression)) return false;

    CollectConsumer<PomTarget> consumer = new CollectConsumer<PomTarget>();

    for (PomDeclarationSearcher searcher : PomDeclarationSearcher.EP_NAME.getExtensions()) {
      searcher.findDeclarationsAt(referenceExpression, 0, consumer);
      if (consumer.getResult().size() > 0) return false;
    }

    return true;
  }

  private void checkStringNameIdentifier(GrReferenceExpression ref) {
    final PsiElement nameElement = ref.getReferenceNameElement();
    if (nameElement == null) return;

    final IElementType elementType = nameElement.getNode().getElementType();
    if (elementType == GroovyTokenTypes.mSTRING_LITERAL || elementType == GroovyTokenTypes.mGSTRING_LITERAL) {
      checkStringLiteral(nameElement, nameElement.getText());
    }
    else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      checkRegexLiteral(nameElement);
    }
  }

  /*
  private static void registerAccessFix(Annotation annotation, PsiElement place, PsiMember refElement) {
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
  */

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
    final PsiElement parent = typeDefinition.getParent();
    if (!typeDefinition.isAnonymous() && !(parent instanceof GrTypeDefinitionBody || parent instanceof GroovyFile)) {
      final TextRange range = getClassHeaderTextRange(typeDefinition);
      final Annotation errorAnnotation =
        myHolder.createErrorAnnotation(range, GroovyBundle.message("class.definition.is.not.expected.here"));
      errorAnnotation.registerFix(new GrMoveClassToCorrectPlaceFix(typeDefinition));
    }
    checkTypeDefinition(myHolder, typeDefinition);
    checkTypeDefinitionModifiers(myHolder, typeDefinition);

    checkDuplicateMethod(typeDefinition.getMethods(), myHolder);
    checkImplementedMethodsOfClass(myHolder, typeDefinition);
    checkConstructors(myHolder, typeDefinition);
    highligtClassReference(myHolder, typeDefinition.getNameIdentifierGroovy());
  }

  private static void checkReferenceList(AnnotationHolder holder,
                                         GrReferenceList list,
                                         boolean interfaceExpected,
                                         String message,
                                         @Nullable IntentionAction fix) {
    if (list == null) return;
    for (GrCodeReferenceElement refElement : list.getReferenceElements()) {
      final PsiElement psiClass = refElement.resolve();
      if (psiClass instanceof PsiClass && ((PsiClass)psiClass).isInterface() != interfaceExpected) {
        if (fix != null) {
          holder.createErrorAnnotation(refElement, message).registerFix(fix);
        }
      }
    }
  }

  private static void checkConstructors(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.isEnum() || typeDefinition.isInterface() || typeDefinition.isAnonymous()) return;
    final PsiClass superClass = typeDefinition.getSuperClass();
    if (superClass == null) return;

    if (GrInheritConstructorContributor.hasInheritConstructorsAnnotation(typeDefinition)) return;

    PsiMethod defConstructor = getDefaultConstructor(superClass);
    boolean hasImplicitDefConstructor = superClass.getConstructors().length == 0;

    final PsiMethod[] constructors = typeDefinition.getCodeConstructors();
    final String qName = superClass.getQualifiedName();
    if (constructors.length == 0) {
      if (!hasImplicitDefConstructor && (defConstructor == null || !PsiUtil.isAccessible(typeDefinition, defConstructor))) {
        final TextRange range = getClassHeaderTextRange(typeDefinition);
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
          holder.createErrorAnnotation(getMethodHeaderTextRange(method),
                                       GroovyBundle.message("there.is.no.default.constructor.available.in.class.0", qName));
        }
      }
    }

    checkRecursiveConstructors(holder, constructors);
  }

  @Override
  public void visitEnumConstant(GrEnumConstant enumConstant) {
    super.visitEnumConstant(enumConstant);
    final GrArgumentList argumentList = enumConstant.getArgumentList();

    if (argumentList != null && argumentList.getNamedArguments().length > 0 && argumentList.getExpressionArguments().length == 0) {
      final PsiMethod constructor = enumConstant.resolveConstructor();
      if (constructor != null) {
        if (!PsiUtil.isConstructorHasRequiredParameters(constructor)) {
          myHolder.createErrorAnnotation(argumentList, GroovyBundle
            .message("the.usage.of.a.map.entry.expression.to.initialize.an.enum.is.currently.not.supported"));
        }
      }
    }
  }

  private static void checkRecursiveConstructors(AnnotationHolder holder, PsiMethod[] constructors) {
    Map<PsiMethod, PsiMethod> nodes = new HashMap<PsiMethod, PsiMethod>(constructors.length);

    Set<PsiMethod> set = ContainerUtil.set(constructors);

    for (PsiMethod constructor : constructors) {
      if (!(constructor instanceof GrMethod)) continue;

      final GrOpenBlock block = ((GrMethod)constructor).getBlock();
      if (block == null) continue;

      final GrStatement[] statements = block.getStatements();
      if (statements.length <= 0 || !(statements[0] instanceof GrConstructorInvocation)) continue;

      final PsiMethod resolved = ((GrConstructorInvocation)statements[0]).resolveMethod();
      if (!set.contains(resolved)) continue;

      nodes.put(constructor, resolved);
    }

    Set<PsiMethod> checked = new HashSet<PsiMethod>();

    Set<PsiMethod> current;
    for (PsiMethod constructor : constructors) {
      if (!checked.add(constructor)) continue;

      current = new HashSet<PsiMethod>();
      current.add(constructor);
      for (constructor = nodes.get(constructor); constructor != null && current.add(constructor); constructor = nodes.get(constructor)) {
        checked.add(constructor);
      }

      if (constructor != null) {
        PsiMethod circleStart = constructor;
        do {
          holder.createErrorAnnotation(getMethodHeaderTextRange(constructor),
                                       GroovyBundle.message("recursive.constructor.invocation"));
          constructor = nodes.get(constructor);
        }
        while (constructor != circleStart);
      }
    }
  }

  public static TextRange getMethodHeaderTextRange(PsiMethod method) {
    final PsiModifierList modifierList = method.getModifierList();
    final PsiParameterList parameterList = method.getParameterList();

    final TextRange textRange = modifierList.getTextRange();
    LOG.assertTrue(textRange != null, method.getClass() + ":" + method.getText());
    int startOffset = textRange.getStartOffset();
    int endOffset = parameterList.getTextRange().getEndOffset() + 1;

    return new TextRange(startOffset, endOffset);
  }

  @Override
  public void visitMethod(GrMethod method) {
    checkMethodDefinitionModifiers(myHolder, method);
    checkMethodWithTypeParamsShouldHaveReturnType(myHolder, method);
    checkInnerMethod(myHolder, method);
    checkMethodParameters(myHolder, method);
  }

  private static void checkMethodWithTypeParamsShouldHaveReturnType(AnnotationHolder holder, GrMethod method) {
    final PsiTypeParameterList parameterList = method.getTypeParameterList();
    if (parameterList != null) {
      final GrTypeElement typeElement = method.getReturnTypeElementGroovy();
      if (typeElement == null) {
        final TextRange parameterListTextRange = parameterList.getTextRange();
        final TextRange range = new TextRange(parameterListTextRange.getEndOffset(), parameterListTextRange.getEndOffset() + 1);
        holder.createErrorAnnotation(range, GroovyBundle.message("method.with.type.parameters.should.have.return.type"));
      }
    }
  }

  private static void checkMethodParameters(AnnotationHolder holder, GrMethod method) {
    if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) return;

    for (GrParameter parameter : method.getParameters()) {
      GrExpression initializerGroovy = parameter.getInitializerGroovy();
      if (initializerGroovy != null) {
        PsiElement assignOperator = parameter.getNameIdentifierGroovy();
        TextRange textRange =
          new TextRange(assignOperator.getTextRange().getEndOffset(), initializerGroovy.getTextRange().getEndOffset());
        holder.createErrorAnnotation(textRange, GroovyBundle.message("default.initializers.are.not.allowed.in.abstract.method"));
      }
    }
  }

  @Nullable
  private static PsiMethod getDefaultConstructor(PsiClass clazz) {
    final String className = clazz.getName();
    if (className == null) return null;
    final PsiMethod[] byName = clazz.findMethodsByName(className, true);
    if (byName.length == 0) return null;
    Outer:
    for (PsiMethod method : byName) {
      if (method.getParameterList().getParametersCount() == 0) return method;
      if (!(method instanceof GrMethod)) continue;
      final GrParameter[] parameters = ((GrMethod)method).getParameterList().getParameters();

      for (GrParameter parameter : parameters) {
        if (!parameter.isOptional()) continue Outer;
      }
      return method;
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
      final GrMember[] members = variableDeclaration.getMembers();
      if (members.length == 0) return;
      final GrMember member = members[0];
      checkAccessModifiers(myHolder, modifiersList, member);
      checkDuplicateModifiers(myHolder, variableDeclaration.getModifierList(), member);

      if (modifiersList.hasExplicitModifier(PsiModifier.VOLATILE) && modifiersList.hasExplicitModifier(PsiModifier.FINAL)) {
        final Annotation annotation =
          myHolder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.volatile.and.final"));
        annotation.registerFix(new GrModifierFix(member, modifiersList, PsiModifier.VOLATILE, true, false));
        annotation.registerFix(new GrModifierFix(member, modifiersList, PsiModifier.FINAL, true, false));
      }

      if (modifiersList.hasExplicitModifier(PsiModifier.NATIVE)) {
        final Annotation annotation = myHolder.createErrorAnnotation(modifiersList, GroovyBundle.message("variable.cannot.be.native"));
        annotation.registerFix(new GrModifierFix(member, modifiersList, PsiModifier.NATIVE, true, false));
      }

      if (modifiersList.hasExplicitModifier(PsiModifier.ABSTRACT)) {
        final Annotation annotation = myHolder.createErrorAnnotation(modifiersList, GroovyBundle.message("variable.cannot.be.abstract"));
        annotation.registerFix(new GrModifierFix(member, modifiersList, PsiModifier.ABSTRACT, true, false));
      }
    }
  }

  @Override
  public void visitVariable(GrVariable variable) {
    checkName(variable);
    if (variable instanceof GrMember) {
      highlightMember(myHolder, ((GrMember)variable));
    }

    PsiNamedElement duplicate = ResolveUtil
      .resolveExistingElement(variable, new DuplicateVariablesProcessor(variable), GrReferenceExpression.class, GrVariable.class);
    if (duplicate == null) {
      if (variable instanceof GrParameter) {
        @SuppressWarnings({"ConstantConditions"})
        final PsiElement parent = variable.getContext().getContext();
        if (parent instanceof GrClosableBlock) {
          duplicate = ResolveUtil.resolveExistingElement((GrClosableBlock)parent, new DuplicateVariablesProcessor(variable),
                                                         GrVariable.class, GrReferenceExpression.class);
        }
      }
    }

    if (duplicate instanceof GrLightParameter && "args".equals(duplicate.getName())) {
      duplicate = null;
    }

    if (duplicate instanceof GrVariable) {
      if (variable instanceof GrField || !(duplicate instanceof GrField)) {
        final String key = duplicate instanceof GrField ? "field.already.defined" : "variable.already.defined";
        myHolder.createErrorAnnotation(variable.getNameIdentifierGroovy(), GroovyBundle.message(key, variable.getName()));
      }
    }
  }

  private void checkName(GrVariable variable) {
    if (!"$".equals(variable.getName())) return;
    myHolder.createErrorAnnotation(variable.getNameIdentifierGroovy(), GroovyBundle.message("incorrect.variable.name"));
  }

  @Override
  public void visitAssignmentExpression(GrAssignmentExpression expression) {
    GrExpression lValue = expression.getLValue();
    if (!PsiUtil.mightBeLValue(lValue)) {
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
    final PsiReference constructorReference = listOrMap.getReference();
    if (constructorReference != null) {
      final PsiElement startToken = listOrMap.getFirstChild();
      if (startToken != null && startToken.getNode().getElementType() == GroovyTokenTypes.mLBRACK) {
        myHolder.createInfoAnnotation(startToken, null).setTextAttributes(DefaultHighlighter.LITERAL_CONVERSION);
      }
      final PsiElement endToken = listOrMap.getLastChild();
      if (endToken != null && endToken.getNode().getElementType() == GroovyTokenTypes.mRBRACK) {
        myHolder.createInfoAnnotation(endToken, null).setTextAttributes(DefaultHighlighter.LITERAL_CONVERSION);
      }
    }

    checkNamedArgs(listOrMap.getNamedArguments(), false);
  }

  @Override
  public void visitClassTypeElement(GrClassTypeElement typeElement) {
    super.visitClassTypeElement(typeElement);

    final GrCodeReferenceElement ref = typeElement.getReferenceElement();
    final GrTypeArgumentList argList = ref.getTypeArgumentList();
    if (argList == null) return;

    final GrTypeElement[] elements = argList.getTypeArgumentElements();
    for (GrTypeElement element : elements) {
      checkTypeArgForPrimitive(element, GroovyBundle.message("primitive.type.parameters.are.not.allowed"));
    }
  }

  private void checkTypeArgForPrimitive(@Nullable GrTypeElement element, String message) {
    if (element == null || !(element.getType() instanceof PsiPrimitiveType)) return;

    myHolder.
      createErrorAnnotation(element, message).
      registerFix(new GrReplacePrimitiveTypeWithWrapperFix(element));
  }

  @Override
  public void visitWildcardTypeArgument(GrWildcardTypeArgument wildcardTypeArgument) {
    super.visitWildcardTypeArgument(wildcardTypeArgument);
    
    checkTypeArgForPrimitive(wildcardTypeArgument.getBoundTypeElement(), GroovyBundle.message("primitive.bound.types.are.not.allowed"));
  }

  private void highlightNamedArgs(GrNamedArgument[] namedArguments) {
    for (GrNamedArgument namedArgument : namedArguments) {
      final GrArgumentLabel label = namedArgument.getLabel();
      if (label != null && label.getExpression() == null && label.getNameElement().getNode().getElementType() != GroovyTokenTypes.mSTAR) {
        myHolder.createInfoAnnotation(label, null).setTextAttributes(DefaultHighlighter.MAP_KEY);
      }
    }
  }

  private void checkNamedArgs(GrNamedArgument[] namedArguments, boolean forArgList) {
    highlightNamedArgs(namedArguments);

    MultiMap<String, GrArgumentLabel> map = new MultiMap<String, GrArgumentLabel>();
    for (GrNamedArgument element : namedArguments) {
      final GrArgumentLabel label = element.getLabel();
      if (label != null) {
        final String name = label.getName();
        if (name != null) {
          map.putValue(name, label);
        }
      }
    }

    for (String key : map.keySet()) {
      final List<GrArgumentLabel> arguments = (List<GrArgumentLabel>)map.get(key);
      if (arguments.size() > 1) {
        for (int i = 1; i < arguments.size(); i++) {
          final GrArgumentLabel label = arguments.get(i);
          if (forArgList) {
            myHolder.createErrorAnnotation(label, GroovyBundle.message("duplicated.named.parameter", key));
          }
          else {
            myHolder.createWarningAnnotation(label, GroovyBundle.message("duplicate.element.in.the.map"));
          }
        }
      }
    }
  }

  @Override
  public void visitNewExpression(GrNewExpression newExpression) {
    GrTypeArgumentList constructorTypeArguments = newExpression.getConstructorTypeArguments();
    if (constructorTypeArguments != null) {
      myHolder.createErrorAnnotation(constructorTypeArguments, GroovyBundle.message("groovy.does.not.support.constructor.type.arguments"));
    }

    final GrTypeElement typeElement = newExpression.getTypeElement();

    if (typeElement instanceof GrBuiltInTypeElement) {
      if (newExpression.getArrayCount() == 0) {
        myHolder.createErrorAnnotation(typeElement, GroovyBundle.message("create.instance.of.built-in.type"));
      }
    }

    if (newExpression.getArrayCount() > 0) return;

    GrCodeReferenceElement refElement = newExpression.getReferenceElement();
    if (refElement == null) return;

    checkDiamonds(refElement, myHolder);

    final PsiElement element = refElement.resolve();
    if (element instanceof PsiClass) {
      PsiClass clazz = (PsiClass)element;
      if (clazz.hasModifierProperty(PsiModifier.ABSTRACT)) {
        if (newExpression.getAnonymousClassDefinition() == null) {
          String message = clazz.isInterface()
                           ? GroovyBundle.message("cannot.instantiate.interface", clazz.getName())
                           : GroovyBundle.message("cannot.instantiate.abstract.class", clazz.getName());
          myHolder.createErrorAnnotation(refElement, message);
        }
        return;
      }
      if (newExpression.getQualifier() != null) {
        if (clazz.hasModifierProperty(PsiModifier.STATIC)) {
          myHolder.createErrorAnnotation(newExpression, GroovyBundle.message("qualified.new.of.static.class"));
        }
      }
      else {
        final PsiClass outerClass = clazz.getContainingClass();
        if (com.intellij.psi.util.PsiUtil.isInnerClass(clazz) && !PsiUtil.hasEnclosingInstanceInScope(outerClass, newExpression, true)) {
          Annotation annotation =
            myHolder.createErrorAnnotation(refElement, GroovyBundle.message("cannot.reference.nonstatic", clazz.getQualifiedName()));
          annotation.setTextAttributes(DefaultHighlighter.UNRESOLVED_ACCESS);
        }
      }
    }
  }

  private static void checkDiamonds(GrCodeReferenceElement refElement, AnnotationHolder holder) {
    final GroovyConfigUtils configUtils = GroovyConfigUtils.getInstance();
    if (configUtils.isVersionAtLeast(refElement, GroovyConfigUtils.GROOVY1_8)) return;

    GrTypeArgumentList typeArgumentList = refElement.getTypeArgumentList();
    if (typeArgumentList != null && typeArgumentList.isDiamond()) {
      final String message = GroovyBundle.message("diamonds.are.not.allowed.in.groovy.0", configUtils.getSDKVersion(refElement));
      holder.createErrorAnnotation(typeArgumentList, message);
    }
  }

  @Override
  public void visitArgumentList(GrArgumentList list) {
    checkNamedArgs(list.getNamedArguments(), true);
  }

  @Override
  public void visitConstructorInvocation(GrConstructorInvocation invocation) {
    final GroovyResolveResult resolveResult = invocation.resolveConstructorGenerics();
    if (resolveResult.getElement() == null) {
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
            String message = GroovyBundle.message("cannot.apply.default.constructor", clazz.getName());
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
  public void visitPackageDefinition(GrPackageDefinition packageDefinition) {
    //todo: if reference isn't resolved it construct package definition
    checkPackage(packageDefinition);
    final GrModifierList modifierList = packageDefinition.getAnnotationList();
    checkAnnotationList(myHolder, modifierList, GroovyBundle.message("package.definition.cannot.have.modifiers"));
  }

  private void checkPackage(GrPackageDefinition packageDefinition) {
    final PsiFile file = packageDefinition.getContainingFile();
    assert file != null;

    PsiDirectory psiDirectory = file.getContainingDirectory();
    if (psiDirectory != null && file instanceof GroovyFile) {
      PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
      if (aPackage != null) {
        String packageName = aPackage.getQualifiedName();
        if (!packageName.equals(packageDefinition.getPackageName())) {
          final Annotation annotation = myHolder.createWarningAnnotation(packageDefinition, GroovyBundle
            .message("wrong.package.name", packageName, aPackage.getQualifiedName()));
          annotation.registerFix(new ChangePackageQuickFix((GroovyFile)packageDefinition.getContainingFile(), packageName));
          annotation.registerFix(new GrMoveToDirFix(packageDefinition.getPackageName()));
        }
      }
    }
  }

  @Override
  public void visitClosure(GrClosableBlock closure) {
    super.visitClosure(closure);
    if (!closure.hasParametersSection() && isClosureAmbiguous(closure)) {
      myHolder.createErrorAnnotation(closure, GroovyBundle.message("ambiguous.code.block"));
    }
  }

  private static boolean isClosureAmbiguous(GrClosableBlock closure) {
    PsiElement place = closure;
    while (true) {
      if (place instanceof GrUnAmbiguousClosureContainer) return false;
      if (PsiUtil.isExpressionStatement(place)) return true;

      PsiElement parent = place.getParent();
      if (parent == null || parent.getFirstChild() != place) return false;
      place = parent;
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
    final IElementType elementType = literal.getFirstChild().getNode().getElementType();
    if (elementType == GroovyTokenTypes.mSTRING_LITERAL || elementType == GroovyTokenTypes.mGSTRING_LITERAL) {
      checkStringLiteral(literal, literal.getText());
    }
    else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      checkRegexLiteral(literal.getFirstChild());
    }
  }

  @Override
  public void visitRegexExpression(GrRegex regex) {
    checkRegexLiteral(regex);
  }

  private void checkRegexLiteral(PsiElement regex) {
    String text = regex.getText();
    String quote = GrStringUtil.getStartQuote(text);

    final GroovyConfigUtils config = GroovyConfigUtils.getInstance();

    if ("$/".equals(quote)) {
      if (!config.isVersionAtLeast(regex, GroovyConfigUtils.GROOVY1_8)) {
        myHolder
          .createErrorAnnotation(regex, GroovyBundle.message("dollar.slash.strings.are.not.allowed.in.0", config.getSDKVersion(regex)));
      }
    }


    String[] parts;
    if (regex instanceof GrRegex) {
      parts = ((GrRegex)regex).getTextParts();
    }
    else {
      parts = new String[]{regex.getFirstChild().getNextSibling().getText()};
    }

    for (String part : parts) {
      if (!GrStringUtil.parseRegexCharacters(part, new StringBuilder(part.length()), null, regex.getText().startsWith("/"))) {
        myHolder.createErrorAnnotation(regex, GroovyBundle.message("illegal.escape.character.in.string.literal"));
        return;
      }
    }

    if ("/".equals(quote)) {
      if (!config.isVersionAtLeast(regex, GroovyConfigUtils.GROOVY1_8)) {
        if (text.contains("\n") || text.contains("\r")) {
          myHolder.createErrorAnnotation(regex, GroovyBundle
            .message("multiline.slashy.strings.are.not.allowed.in.groovy.0", config.getSDKVersion(regex)));
          return;
        }
      }
    }
  }

  @Override
  public void visitGStringExpression(GrString gstring) {
    for (String part : gstring.getTextParts()) {
      if (!GrStringUtil.parseStringCharacters(part, new StringBuilder(part.length()), null)) {
        myHolder.createErrorAnnotation(gstring, GroovyBundle.message("illegal.escape.character.in.string.literal"));
        return;
      }
    }
  }

  private void checkStringLiteral(PsiElement literal, String text) {

    StringBuilder builder = new StringBuilder(text.length());
    String quote = GrStringUtil.getStartQuote(text);
    if (quote.isEmpty()) return;

    String substring = text.substring(quote.length());
    if (!GrStringUtil.parseStringCharacters(substring, new StringBuilder(text.length()), null)) {
      myHolder.createErrorAnnotation(literal, GroovyBundle.message("illegal.escape.character.in.string.literal"));
      return;
    }

    int[] offsets = new int[substring.length() + 1];
    boolean result = GrStringUtil.parseStringCharacters(substring, builder, offsets);
    LOG.assertTrue(result);
    if (!builder.toString().endsWith(quote) || substring.charAt(offsets[builder.length() - quote.length()]) == '\\') {
      myHolder.createErrorAnnotation(literal, GroovyBundle.message("string.end.expected"));
    }
  }

  @Override
  public void visitForInClause(GrForInClause forInClause) {
    final GrVariable var = forInClause.getDeclaredVariable();
    if (var == null) return;
    final GrModifierList modifierList = var.getModifierList();
    if (modifierList == null) return;
    final PsiElement[] modifiers = modifierList.getModifiers();
    for (PsiElement modifier : modifiers) {
      if (modifier instanceof PsiAnnotation) continue;
      final String modifierText = modifier.getText();
      if (PsiModifier.FINAL.equals(modifierText)) continue;
      if (GrModifier.DEF.equals(modifierText)) continue;
      myHolder.createErrorAnnotation(modifier, GroovyBundle.message("not.allowed.modifier.in.forin", modifierText));
    }
  }

  @Override
  public void visitFile(GroovyFileBase file) {
    final PsiClass scriptClass = file.getScriptClass();
    if (scriptClass != null) {
      checkDuplicateMethod(scriptClass.getMethods(), myHolder);
    }
  }

  @Override
  public void visitAnnotation(GrAnnotation annotation) {
    super.visitAnnotation(annotation);
    final GrCodeReferenceElement ref = annotation.getClassReference();
    final PsiElement resolved = ref.resolve();

    assert resolved == null || resolved instanceof PsiClass;
    if (resolved != null && !((PsiClass)resolved).isAnnotationType()) {
      myHolder.createErrorAnnotation(ref, GroovyBundle.message("class.is.not.annotation", ((PsiClass)resolved).getQualifiedName()));
    }
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
    if (GroovyConfigUtils.getInstance().isVersionAtLeast(expression, GroovyConfigUtils.GROOVY1_8)) return;

    final GrReferenceExpression qualifier = expression instanceof GrThisReferenceExpression
                                            ? ((GrThisReferenceExpression)expression).getQualifier()
                                            : ((GrSuperReferenceExpression)expression).getQualifier();
    if (qualifier == null) {
      if (expression instanceof GrSuperReferenceExpression) { //'this' refers to java.lang.Class<ThisClass> in static context
        final GrMethod method = PsiTreeUtil.getParentOfType(expression, GrMethod.class);
        if (method != null && method.hasModifierProperty(PsiModifier.STATIC)) {
          Annotation annotation =
            holder.createInfoAnnotation(expression, GroovyBundle.message("cannot.reference.nonstatic", expression.getText()));
          annotation.setTextAttributes(DefaultHighlighter.UNRESOLVED_ACCESS);
        }
      }
    }
    else {
      final PsiElement resolved = qualifier.resolve();
      if (resolved instanceof PsiClass) {
        if (PsiTreeUtil.isAncestor(resolved, expression, true)) {
          if (!PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, expression, true)) {
            Annotation annotation =
              holder.createInfoAnnotation(expression, GroovyBundle.message("cannot.reference.nonstatic", expression.getText()));
            annotation.setTextAttributes(DefaultHighlighter.UNRESOLVED_ACCESS);
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
    if (typeDefinition.hasModifierProperty(PsiModifier.ABSTRACT)) return;
    if (typeDefinition.isAnnotationType()) return;
    if (typeDefinition instanceof GrTypeParameter) return;

    Collection<CandidateInfo> collection = OverrideImplementUtil.getMethodsToOverrideImplement(typeDefinition, true);
    if (collection.isEmpty()) return;

    final PsiElement element = collection.iterator().next().getElement();
    assert element instanceof PsiNamedElement;
    String notImplementedMethodName = ((PsiNamedElement)element).getName();

    final TextRange range = getClassHeaderTextRange(typeDefinition);
    final Annotation annotation = holder.createErrorAnnotation(range,
                                                               GroovyBundle.message("method.is.not.implemented", notImplementedMethodName));
    registerImplementsMethodsFix(typeDefinition, annotation);
  }

  private static TextRange getClassHeaderTextRange(GrTypeDefinition clazz) {
    final GrModifierList modifierList = clazz.getModifierList();
    final int startOffset = modifierList != null ? modifierList.getTextOffset() : clazz.getTextOffset();
    final GrImplementsClause implementsClause = clazz.getImplementsClause();

    final int endOffset;
    if (implementsClause != null) {
      endOffset = implementsClause.getTextRange().getEndOffset();
    }
    else {
      final GrExtendsClause extendsClause = clazz.getExtendsClause();
      if (extendsClause != null) {
        endOffset = extendsClause.getTextRange().getEndOffset();
      }
      else {
        endOffset = clazz.getNameIdentifierGroovy().getTextRange().getEndOffset();
      }
    }
    return new TextRange(startOffset, endOffset);
  }

  private static void registerImplementsMethodsFix(GrTypeDefinition typeDefinition, Annotation annotation) {
    annotation.registerFix(QuickFixFactory.getInstance().createImplementMethodsFix(typeDefinition));
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
      annotation.registerFix(new GrModifierFix(method, method.getModifierList(), PsiModifier.ABSTRACT, false, false));
    }
    if (makeClassAbstract) {
      final PsiClass containingClass = method.getContainingClass();
      LOG.assertTrue(containingClass != null);
      final GrModifierList list = (GrModifierList)containingClass.getModifierList();
      LOG.assertTrue(list != null);
      annotation.registerFix(new GrModifierFix(containingClass, list, PsiModifier.ABSTRACT, false, true));
    }
  }

  private static void checkMethodDefinitionModifiers(AnnotationHolder holder, GrMethod method) {
    final GrModifierList modifiersList = method.getModifierList();
    checkAccessModifiers(holder, modifiersList, method);
    checkDuplicateModifiers(holder, modifiersList, method);
    checkOverrideAnnotation(holder, modifiersList, method);

    //script methods
    boolean isMethodAbstract = modifiersList.hasExplicitModifier(PsiModifier.ABSTRACT);
    final boolean isMethodStatic = modifiersList.hasExplicitModifier(PsiModifier.STATIC);
    if (method.getParent() instanceof GroovyFileBase) {
      if (isMethodAbstract) {
        final Annotation annotation =
          holder.createErrorAnnotation(modifiersList, GroovyBundle.message("script.cannot.have.modifier.abstract"));
        registerAbstractMethodFix(annotation, method, false);
      }

      if (modifiersList.hasExplicitModifier(PsiModifier.NATIVE)) {
        final Annotation annotation =
          holder.createErrorAnnotation(modifiersList, GroovyBundle.message("script.cannot.have.modifier.native"));
        annotation.registerFix(new GrModifierFix(method, modifiersList, PsiModifier.NATIVE, false, false));
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
            annotation.registerFix(new GrModifierFix(method, modifiersList, PsiModifier.STATIC, true, false));
          }

          if (modifiersList.hasExplicitModifier(PsiModifier.PRIVATE)) {
            final Annotation annotation =
              holder.createErrorAnnotation(modifiersList, GroovyBundle.message("interface.must.have.no.private.method"));
            annotation.registerFix(new GrModifierFix(method, modifiersList, PsiModifier.PRIVATE, true, false));
          }
        }
        else if (containingTypeDef.isAnonymous()) {
          //anonymous class
          if (isMethodStatic) {
            final Annotation annotation =
              holder.createErrorAnnotation(modifiersList, GroovyBundle.message("static.declaration.in.inner.class"));
            annotation.registerFix(new GrModifierFix(method, modifiersList, PsiModifier.STATIC, false, false));
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

          if (!typeDefModifiersList.hasExplicitModifier(PsiModifier.ABSTRACT)) {
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
    catch (IndexNotReadyException ignored) {
      //nothing to do
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
          if (modifierList.hasExplicitModifier(PsiModifier.FINAL)) {
            final Annotation annotation = holder
              .createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), GroovyBundle.message("final.class.cannot.be.extended"));
            annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, PsiModifier.FINAL, false, false));
          }
        }
      }
    }

    if (modifiersList.hasExplicitModifier(PsiModifier.ABSTRACT) && modifiersList.hasExplicitModifier(PsiModifier.FINAL)) {
      final Annotation annotation =
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.abstract.and.final"));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, PsiModifier.FINAL, false, false));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, PsiModifier.ABSTRACT, false, false));
    }

    if (modifiersList.hasExplicitModifier(PsiModifier.TRANSIENT)) {
      final Annotation annotation =
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("modifier.transient.not.allowed.here"));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, PsiModifier.TRANSIENT, false, false));
    }
    if (modifiersList.hasExplicitModifier(PsiModifier.VOLATILE)) {
      final Annotation annotation = holder.createErrorAnnotation(modifiersList, GroovyBundle.message("modifier.volatile.not.allowed.here"));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, PsiModifier.VOLATILE, false, false));
    }

    /**** interface ****/
    if (typeDefinition.isInterface()) {
      if (modifiersList.hasExplicitModifier(PsiModifier.FINAL)) {
        final Annotation annotation =
          holder.createErrorAnnotation(modifiersList, GroovyBundle.message("intarface.cannot.have.modifier.final"));
        annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, PsiModifier.FINAL, false, false));
      }
    }
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
    boolean hasPrivate = modifierList.hasExplicitModifier(PsiModifier.PRIVATE);
    boolean hasPublic = modifierList.hasExplicitModifier(PsiModifier.PUBLIC);
    boolean hasProtected = modifierList.hasExplicitModifier(PsiModifier.PROTECTED);

    if (hasPrivate && hasPublic || hasPrivate && hasProtected || hasPublic && hasProtected) {
      final Annotation annotation = holder.createErrorAnnotation(modifierList, GroovyBundle.message("illegal.combination.of.modifiers"));
      if (hasPrivate) {
        annotation.registerFix(new GrModifierFix(member, modifierList, PsiModifier.PRIVATE, false, false));
      }
      if (hasProtected) {
        annotation.registerFix(new GrModifierFix(member, modifierList, PsiModifier.PROTECTED, false, false));
      }
      if (hasPublic) {
        annotation.registerFix(new GrModifierFix(member, modifierList, PsiModifier.PUBLIC, false, false));
      }
    }
  }

  private static void checkDuplicateMethod(PsiMethod[] methods, AnnotationHolder holder) {
    MultiMap<MethodSignature, PsiMethod> map = GrClosureSignatureUtil.findMethodSignatures(methods);
    processMethodDuplicates(map, holder);
  }

  protected static void processMethodDuplicates(MultiMap<MethodSignature, PsiMethod> map, AnnotationHolder holder) {
    for (MethodSignature signature : map.keySet()) {
      Collection<PsiMethod> methods = map.get(signature);
      if (methods.size() > 1) {
        for (Iterator<PsiMethod> iterator = methods.iterator(); iterator.hasNext(); ) {
          PsiMethod method = iterator.next();
          if (method instanceof LightElement) iterator.remove();
        }

        if (methods.size() < 2) continue;
        String signaturePresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
        for (PsiMethod method : methods) {
          //noinspection ConstantConditions
          holder.createErrorAnnotation(getMethodHeaderTextRange(method), GroovyBundle
            .message("method.duplicate", signaturePresentation, method.getContainingClass().getName()));
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
      if (!configUtils.isVersionAtLeast(typeDefinition, GroovyConfigUtils.GROOVY1_7)) {
        holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), GroovyBundle.message("anonymous.classes.are.not.supported",
                                                                                                    configUtils
                                                                                                      .getSDKVersion(typeDefinition)));
      }
    }
    else if (typeDefinition.getContainingClass() != null && !(typeDefinition instanceof GrEnumTypeDefinition)) {
      if (!configUtils.isVersionAtLeast(typeDefinition, GroovyConfigUtils.GROOVY1_7)) {
        holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(),
                                     GroovyBundle.message("inner.classes.are.not.supported", configUtils.getSDKVersion(typeDefinition)));
      }
    }

    final GrImplementsClause implementsClause = typeDefinition.getImplementsClause();
    final GrExtendsClause extendsClause = typeDefinition.getExtendsClause();


    if (typeDefinition.isInterface()) {
      checkReferenceList(holder, extendsClause, true, GroovyBundle.message("no.interface.expected.here"), null);
      if (implementsClause != null) {
        holder.createErrorAnnotation(implementsClause, GroovyBundle.message("no.implements.clause.allowed.for.interface"));
      }
    }
    else {
      checkReferenceList(holder, extendsClause, false, GroovyBundle.message("no.interface.expected.here"),
                         ExtendsImplementsFix.MOVE_TO_IMPLEMENTS_LIST);
      checkReferenceList(holder, implementsClause, true, GroovyBundle.message("no.class.expected.here"),
                         ExtendsImplementsFix.MOVE_TO_EXTENDS_LIST);
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
      holder.createErrorAnnotation(getClassHeaderTextRange(typeDefinition),
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


  private static void registerReferenceFixes(GrReferenceExpression refExpr, Annotation annotation) {
    PsiClass targetClass = QuickfixUtil.findTargetClass(refExpr);
    if (targetClass == null) return;

    addDynamicAnnotation(annotation, refExpr);
    if (targetClass instanceof GrMemberOwner && targetClass.isWritable()) {
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
      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(referenceExpression, false);
      if (argumentTypes != null) {
        annotation.registerFix(new DynamicMethodFix(referenceExpression, argumentTypes), referenceExpression.getTextRange());
      }
    }
    else {
      annotation.registerFix(new DynamicPropertyFix(referenceExpression), referenceExpression.getTextRange());
    }
  }

  private static void highlightMemberResolved(AnnotationHolder holder, GrReferenceExpression refExpr, PsiMember member) {
    boolean isStatic = member.hasModifierProperty(PsiModifier.STATIC);
    final PsiElement refNameElement = getElementToHighlight(refExpr);
    Annotation annotation = holder.createInfoAnnotation(refNameElement, null);

    if (member instanceof PsiField) {
      annotation.setTextAttributes(isStatic ? DefaultHighlighter.STATIC_FIELD : DefaultHighlighter.INSTANCE_FIELD);
      return;
    }
    else if (member instanceof GrAccessorMethod) {
      annotation
        .setTextAttributes(isStatic ? DefaultHighlighter.STATIC_PROPERTY_REFERENCE : DefaultHighlighter.INSTANCE_PROPERTY_REFERENCE);
      return;
    }
    if (member instanceof PsiMethod) {
      if (GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)member)) {
        annotation
          .setTextAttributes(isStatic ? DefaultHighlighter.STATIC_PROPERTY_REFERENCE : DefaultHighlighter.INSTANCE_PROPERTY_REFERENCE);
      }
      else {
        annotation.setTextAttributes(isStatic ? DefaultHighlighter.STATIC_METHOD_ACCESS : DefaultHighlighter.METHOD_CALL);
      }
    }
    if (member instanceof PsiClass) {
      highligtClassReference(holder, refExpr);
    }
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

  private static void checkSingleResolvedElement(AnnotationHolder holder,
                                                 GrReferenceElement refElement,
                                                 GroovyResolveResult resolveResult,
                                                 boolean highlightError) {
    final PsiElement resolved = resolveResult.getElement();
    final PsiElement toHighlight = getElementToHighlight(refElement);
    if (resolved == null) {
      String message = GroovyBundle.message("cannot.resolve", refElement.getReferenceName());

      // Register quickfix

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
        UnresolvedReferenceQuickFixProvider.registerReferenceFixes(refElement, new QuickFixActionRegistrarAdapter(annotation));
        OrderEntryFix.registerFixes(new QuickFixActionRegistrarAdapter(annotation), refElement);
      }
    }
    else if (!resolveResult.isAccessible()) {
      String message = GroovyBundle.message("cannot.access", refElement.getReferenceName());
      holder.createWarningAnnotation(toHighlight, message);
    }
  }

  @NotNull
  public static PsiElement getElementToHighlight(@NotNull GrReferenceElement refElement) {
    final PsiElement refNameElement = refElement.getReferenceNameElement();
    return refNameElement != null ? refNameElement : refElement;
  }


  private static void registerAddImportFixes(GrReferenceElement refElement, Annotation annotation) {
    final String referenceName = refElement.getReferenceName();
    //noinspection ConstantConditions
    if (StringUtil.isEmpty(referenceName) ||
        (!(refElement instanceof GrCodeReferenceElement) && Character.isLowerCase(referenceName.charAt(0)))) {
      return;
    }

    annotation.registerFix(new GroovyAddImportAction(refElement));
  }

  private static void registerCreateClassByTypeFix(GrReferenceElement refElement, Annotation annotation) {
    GrPackageDefinition packageDefinition = PsiTreeUtil.getParentOfType(refElement, GrPackageDefinition.class);
    if (packageDefinition == null && refElement.getQualifier() == null) {
      PsiElement parent = refElement.getParent();
      if (parent instanceof GrNewExpression &&
          refElement.getManager().areElementsEquivalent(((GrNewExpression)parent).getReferenceElement(), refElement)) {
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
      final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      holder.createInfoAnnotation(identifier, null).setTextAttributes(
        isStatic ? DefaultHighlighter.STATIC_FIELD : DefaultHighlighter.INSTANCE_FIELD);
    }
  }

  private static void highlightAnnotation(AnnotationHolder holder, PsiElement refElement, GroovyResolveResult result) {
    PsiElement element = result.getElement();
    PsiElement parent = refElement.getParent();
    if (element instanceof PsiClass) {
      if (((PsiClass)element).isAnnotationType() && !(parent instanceof GrImportStatement)) {
        final TextRange range = refElement.getTextRange();
        Annotation annotation = holder.createInfoAnnotation(new TextRange(range.getStartOffset() - 1, range.getEndOffset()), null);
        annotation.setTextAttributes(DefaultHighlighter.ANNOTATION);
        GroovyPsiElement context = result.getCurrentFileResolveContext();
        if (context instanceof GrImportStatement) {
          final GrCodeReferenceElement importReference = ((GrImportStatement)context).getImportReference();
          LOG.assertTrue(importReference != null);
          annotation = holder.createInfoAnnotation(importReference, null);
          annotation.setTextAttributes(DefaultHighlighter.ANNOTATION);
        }
      }
      else {
        highligtClassReference(holder, refElement);
      }
    }
  }

  private static void highligtClassReference(AnnotationHolder holder, PsiElement classReference) {
    final Annotation annotation = holder.createInfoAnnotation(classReference, null);
    annotation.setTextAttributes(DefaultHighlighter.CLASS_REFERENCE);
  }

  public static class DuplicateVariablesProcessor extends PropertyResolverProcessor {
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
      return modifierList.hasExplicitModifier(PsiModifier.PUBLIC) ||
             modifierList.hasExplicitModifier(PsiModifier.PROTECTED) ||
             modifierList.hasExplicitModifier(PsiModifier.PRIVATE);
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

  private static class QuickFixActionRegistrarAdapter implements QuickFixActionRegistrar {
    private final Annotation myAnnotation;

    public QuickFixActionRegistrarAdapter(Annotation annotation) {
      myAnnotation = annotation;
    }

    @Override
    public void register(IntentionAction action) {
      myAnnotation.registerFix(action);
    }

    @Override
    public void register(TextRange fixRange, IntentionAction action, HighlightDisplayKey key) {
      myAnnotation.registerFix(action, fixRange, key);
    }

    @Override
    public void unregister(Condition<IntentionAction> condition) {
      throw new UnsupportedOperationException();
    }
  }
}

