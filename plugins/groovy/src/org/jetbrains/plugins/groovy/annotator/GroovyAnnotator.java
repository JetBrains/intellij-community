/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMethodBodyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorMatchingSuperFix;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteMethodBodyFix;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.refactoring.introduceParameter.ExpressionConverter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.*;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.*;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.GrInheritConstructorContributor;

import java.util.*;

import static com.intellij.psi.PsiModifier.*;
import static org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter.*;

/**
 * @author ven
 */
@SuppressWarnings({"unchecked"})
public class GroovyAnnotator extends GroovyElementVisitor {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.GroovyAnnotator");

  private final AnnotationHolder myHolder;

  public GroovyAnnotator(@NotNull AnnotationHolder holder) {
    myHolder = holder;
  }

  @Override
  public void visitTypeArgumentList(GrTypeArgumentList typeArgumentList) {
    PsiElement parent = typeArgumentList.getParent();
    if (!(parent instanceof GrReferenceElement)) return;

    final GroovyResolveResult resolveResult = ((GrReferenceElement)parent).advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();

    if (resolved == null) return;

    if (!(resolved instanceof PsiTypeParameterListOwner)) {
      myHolder.createWarningAnnotation(typeArgumentList, GroovyBundle.message("type.argument.list.is.not.allowed.here"));
      return;
    }

    if (parent instanceof GrCodeReferenceElement) {
      if (!checkDiamonds((GrCodeReferenceElement)parent, myHolder)) return;
    }

    final PsiTypeParameter[] parameters = ((PsiTypeParameterListOwner)resolved).getTypeParameters();
    final GrTypeElement[] arguments = typeArgumentList.getTypeArgumentElements();

    if (arguments.length != parameters.length) {
      myHolder.createWarningAnnotation(typeArgumentList,
                                       GroovyBundle.message("wrong.number.of.type.arguments", arguments.length, parameters.length));
      return;
    }

    for (int i = 0; i < parameters.length; i++) {
      PsiTypeParameter parameter = parameters[i];
      final PsiClassType[] superTypes = parameter.getExtendsListTypes();
      final PsiType argType = arguments[i].getType();
      for (PsiClassType superType : superTypes) {
        final PsiType substitutedSuper = substitutor.substitute(superType);
        if (substitutedSuper != null && !substitutedSuper.isAssignableFrom(argType)) {
          myHolder.createWarningAnnotation(arguments[i], GroovyBundle
            .message("type.argument.0.is.not.in.its.bound.should.extend.1", argType.getCanonicalText(), superType.getCanonicalText()));
          break;
        }
      }
    }
  }

  @Override
  public void visitNamedArgument(GrNamedArgument argument) {
    final PsiElement parent = argument.getParent().getParent();
    if (parent instanceof GrIndexProperty) {
      myHolder.createErrorAnnotation(argument, GroovyBundle.message("named.arguments.are.not.allowed.inside.index.operations"));
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
  public void visitTryStatement(GrTryCatchStatement statement) {
    final GrCatchClause[] clauses = statement.getCatchClauses();
    List<PsiType> usedExceptions = new ArrayList<PsiType>();

    for (GrCatchClause clause : clauses) {
      final GrParameter parameter = clause.getParameter();
      if (parameter == null) continue;

      final GrTypeElement typeElement = parameter.getTypeElementGroovy();
      PsiType type = typeElement != null ? typeElement.getType() : TypesUtil.createType(CommonClassNames.JAVA_LANG_EXCEPTION, statement);

      if (typeElement instanceof GrDisjunctionTypeElement) {
        final GrTypeElement[] elements = ((GrDisjunctionTypeElement)typeElement).getTypeElements();
        PsiType[] types = new PsiType[elements.length];
        for (int i = 0; i < elements.length; i++) {
          types[i] = elements[i].getType();
        }

        List<PsiType> usedInsideDisjunction = new ArrayList<PsiType>();
        for (int i = 0; i < types.length; i++) {
          if (checkExceptionUsed(usedExceptions, parameter, elements[i], types[i])) {
            usedInsideDisjunction.add(types[i]);
            for (int j = 0; j < types.length; j++) {
              if (i != j && types[j].isAssignableFrom(types[i])) {
                myHolder.createWarningAnnotation(elements[i], GroovyBundle.message("unnecessary.type", types[i].getCanonicalText(),
                                                                                   types[j].getCanonicalText()))
                  .registerFix(new GrRemoveExceptionFix(true));
              }
            }
          }
        }

        usedExceptions.addAll(usedInsideDisjunction);
      }
      else {
        if (checkExceptionUsed(usedExceptions, parameter, typeElement, type)) {
          usedExceptions.add(type);
        }
      }
    }
  }

  @Override
  public void visitCatchClause(GrCatchClause clause) {
    final GrParameter parameter = clause.getParameter();
    if (parameter == null) return;

    final GrTypeElement typeElement = parameter.getTypeElementGroovy();
    if (typeElement != null) {
      final PsiType type = typeElement.getType();
      if (type instanceof PsiClassType && ((PsiClassType)type).resolve() == null) return; //don't highlight unresolved types
      final PsiClassType throwable = TypesUtil.createType(CommonClassNames.JAVA_LANG_THROWABLE, clause);
      if (!throwable.isAssignableFrom(type)) {
        myHolder.createErrorAnnotation(typeElement, GroovyBundle.message("catch.statement.parameter.type.should.be.a.subclass.of.throwable"));
      }
    }
  }

  @Override
  public void visitDocComment(GrDocComment comment) {
    String text = comment.getText();
    if (!text.endsWith("*/")) {
      TextRange range = comment.getTextRange();
      myHolder.createErrorAnnotation(new TextRange(range.getEndOffset() - 1, range.getEndOffset()), GroovyBundle.message("doc.end.expected"));
    }
  }

  @Override
  public void visitVariableDeclaration(GrVariableDeclaration variableDeclaration) {
    if (variableDeclaration.isTuple()) {
      final GrModifierList list = variableDeclaration.getModifierList();

      final PsiElement last = PsiUtil.skipWhitespacesAndComments(list.getLastChild(), false);
      if (last != null) {
        final IElementType type = last.getNode().getElementType();
        if (type != GroovyTokenTypes.kDEF) {
          myHolder.createErrorAnnotation(list, GroovyBundle.message("tuple.declaration.should.end.with.def.modifier"));
        }
      }
      else {
        myHolder.createErrorAnnotation(list, GroovyBundle.message("tuple.declaration.should.end.with.def.modifier"));
      }
    }
  }

  private boolean checkExceptionUsed(List<PsiType> usedExceptions, GrParameter parameter, GrTypeElement typeElement, PsiType type) {
    for (PsiType exception : usedExceptions) {
      if (exception.isAssignableFrom(type)) {
        myHolder.createWarningAnnotation(typeElement != null ? typeElement : parameter.getNameIdentifierGroovy(),
                                         GroovyBundle.message("exception.0.has.already.been.caught", type.getCanonicalText()))
          .registerFix(new GrRemoveExceptionFix(parameter.getTypeElementGroovy() instanceof GrDisjunctionTypeElement));
        return false;
      }
    }
    return true;
  }

  @Override
  public void visitReferenceExpression(final GrReferenceExpression referenceExpression) {
    checkStringNameIdentifier(referenceExpression);
    checkThisOrSuperReferenceExpression(referenceExpression, myHolder);
    checkFinalFieldAccess(referenceExpression);
    checkFinalParameterAccess(referenceExpression);
    if (ResolveUtil.isKeyOfMap(referenceExpression)) {
      PsiElement nameElement = referenceExpression.getReferenceNameElement();
      LOG.assertTrue(nameElement != null);
      myHolder.createInfoAnnotation(nameElement, null).setTextAttributes(MAP_KEY);
    }
    else if (GrUnresolvedAccessInspection.isClassReference(referenceExpression)) {
      PsiElement nameElement = referenceExpression.getReferenceNameElement();
      LOG.assertTrue(nameElement != null);
      myHolder.createInfoAnnotation(nameElement, null).setTextAttributes(KEYWORD);
    }
  }

  private void checkFinalParameterAccess(GrReferenceExpression ref) {
    final PsiElement resolved = ref.resolve();

    if (resolved instanceof GrParameter) {
      final GrParameter parameter = (GrParameter)resolved;
      if (parameter.isPhysical() && parameter.hasModifierProperty(FINAL) && PsiUtil.isLValue(ref)) {
        if (parameter.getDeclarationScope() instanceof PsiMethod) {
          myHolder.createErrorAnnotation(ref, GroovyBundle.message("cannot.assign.a.value.to.final.parameter.0", parameter.getName()));
        }
      }
    }
  }

  private void checkFinalFieldAccess(@NotNull GrReferenceExpression ref) {
    final PsiElement resolved = ref.resolve();

    if (resolved instanceof GrField && resolved.isPhysical() && ((GrField)resolved).hasModifierProperty(FINAL) && PsiUtil.isLValue(ref)) {
      final GrField field = (GrField)resolved;

      final PsiClass containingClass = field.getContainingClass();
      if (containingClass != null && PsiTreeUtil.isAncestor(containingClass, ref, true)) {
        GrMember container = GrHighlightUtil.findClassMemberContainer(ref, containingClass);

        if (field.hasModifierProperty(STATIC)) {
          if (container instanceof GrClassInitializer && ((GrClassInitializer)container).isStatic()) {
            return;
          }
        }
        else {
          if (container instanceof GrMethod && ((GrMethod)container).isConstructor() ||
              container instanceof GrClassInitializer && !((GrClassInitializer)container).isStatic()) {
            return;
          }
        }

        myHolder.createErrorAnnotation(ref, GroovyBundle.message("cannot.assign.a.value.to.final.field.0", field.getName()));
      }
    }
  }

  private void checkStringNameIdentifier(GrReferenceExpression ref) {
    final PsiElement nameElement = ref.getReferenceNameElement();
    if (nameElement == null) return;

    final IElementType elementType = nameElement.getNode().getElementType();
    if (elementType == GroovyTokenTypes.mSTRING_LITERAL || elementType == GroovyTokenTypes.mGSTRING_LITERAL) {
      checkStringLiteral(nameElement);
    }
    else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      checkRegexLiteral(nameElement);
    }
  }

  @Override
  public void visitTypeDefinitionBody(GrTypeDefinitionBody typeDefinitionBody) {
    final PsiElement parent = typeDefinitionBody.getParent();
    if (parent instanceof GrAnonymousClassDefinition) {
      final PsiElement prev = typeDefinitionBody.getPrevSibling();
      if (PsiUtil.isLineFeed(prev)) {
        myHolder.createErrorAnnotation(typeDefinitionBody, GroovyBundle.message("ambiguous.code.block"));
      }
    }
  }

  @Override
  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    final PsiElement parent = typeDefinition.getParent();
    if (!(typeDefinition.isAnonymous() ||
          parent instanceof GrTypeDefinitionBody ||
          parent instanceof GroovyFile ||
          typeDefinition instanceof GrTypeParameter)) {
      final TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
      final Annotation errorAnnotation =
        myHolder.createErrorAnnotation(range, GroovyBundle.message("class.definition.is.not.expected.here"));
      errorAnnotation.registerFix(new GrMoveClassToCorrectPlaceFix(typeDefinition));
    }
    checkTypeDefinition(myHolder, typeDefinition);

    checkDuplicateMethod(typeDefinition, myHolder);
    checkImplementedMethodsOfClass(myHolder, typeDefinition);
    checkConstructors(myHolder, typeDefinition);

    checkAnnotationCollector(myHolder, typeDefinition);

    checkSameNameMethodsWithDifferentAccessModifiers(myHolder, typeDefinition.getCodeMethods());
  }

  private static void checkSameNameMethodsWithDifferentAccessModifiers(AnnotationHolder holder, GrMethod[] methods) {
    MultiMap<String, GrMethod> map = MultiMap.create();
    for (GrMethod method : methods) {
      map.putValue(method.getName(), method);
    }

    for (Map.Entry<String, Collection<GrMethod>> entry : map.entrySet()) {
      Collection<GrMethod> collection = entry.getValue();
      if (collection.size() > 1 && !sameAccessModifier(collection)) {
        for (GrMethod method : collection) {
          holder.createErrorAnnotation(GrHighlightUtil.getMethodHeaderTextRange(method), GroovyBundle.message("mixing.private.and.public.protected.methods.of.the.same.name"));
        }
      }
    }
  }

  private static boolean sameAccessModifier(Collection<GrMethod> collection) {
    Iterator<GrMethod> iterator = collection.iterator();
    GrMethod method = iterator.next();
    boolean  privateAccess = PsiModifier.PRIVATE.equals(VisibilityUtil.getVisibilityModifier(method.getModifierList()));

    while (iterator.hasNext()) {
      GrMethod next = iterator.next();
      if (privateAccess != PsiModifier.PRIVATE.equals(VisibilityUtil.getVisibilityModifier(next.getModifierList()))) {
        return false;
      }
    }

    return true;
  }

  private static void checkAnnotationCollector(AnnotationHolder holder, GrTypeDefinition definition) {
    if (definition.isAnnotationType() &&
        GrAnnotationCollector.findAnnotationCollector(definition) != null &&
        definition.getCodeMethods().length > 0) {
      holder.createErrorAnnotation(definition.getNameIdentifierGroovy(), GroovyBundle.message("annotation.collector.cannot.have.attributes"));
    }
  }

  private static void checkConstructors(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.isEnum() || typeDefinition.isInterface() || typeDefinition.isAnonymous() || typeDefinition instanceof GrTypeParameter) return;
    final PsiClass superClass = typeDefinition.getSuperClass();
    if (superClass == null) return;

    if (GrInheritConstructorContributor.hasInheritConstructorsAnnotation(typeDefinition)) return;

    PsiMethod defConstructor = getDefaultConstructor(superClass);
    boolean hasImplicitDefConstructor = superClass.getConstructors().length == 0;

    final PsiMethod[] constructors = typeDefinition.getCodeConstructors();
    final String qName = superClass.getQualifiedName();
    if (constructors.length == 0) {
      if (!hasImplicitDefConstructor && (defConstructor == null || !PsiUtil.isAccessible(typeDefinition, defConstructor))) {
        final TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
        holder.createErrorAnnotation(range, GroovyBundle.message("there.is.no.default.constructor.available.in.class.0", qName))
          .registerFix(new CreateConstructorMatchingSuperFix(typeDefinition));
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
          holder.createErrorAnnotation(GrHighlightUtil.getMethodHeaderTextRange(method),
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

    if (argumentList != null && PsiImplUtil.hasNamedArguments(argumentList) && !PsiImplUtil.hasExpressionArguments(argumentList)) {
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
          holder.createErrorAnnotation(GrHighlightUtil.getMethodHeaderTextRange(constructor),
                                       GroovyBundle.message("recursive.constructor.invocation"));
          constructor = nodes.get(constructor);
        }
        while (constructor != circleStart);
      }
    }
  }

  @Override
  public void visitOpenBlock(GrOpenBlock block) {
    if (block.getParent() instanceof GrMethod) {
      final GrMethod method = (GrMethod)block.getParent();
      if (method.hasModifierProperty(ABSTRACT)) {
        final Annotation annotation = myHolder.createErrorAnnotation(block, GroovyBundle.message("abstract.methods.must.not.have.body"));
        registerMakeAbstractMethodNotAbstractFix(annotation, method, true);
      }
    }
  }

  @Override
  public void visitMethod(GrMethod method) {
    checkMethodWithTypeParamsShouldHaveReturnType(myHolder, method);
    checkInnerMethod(myHolder, method);
    checkOptionalParametersInAbstractMethod(myHolder, method);

    final PsiElement nameIdentifier = method.getNameIdentifierGroovy();
    if (nameIdentifier.getNode().getElementType() == GroovyTokenTypes.mSTRING_LITERAL) {
      checkStringLiteral(nameIdentifier);
    }

    GrOpenBlock block = method.getBlock();
    if (block != null && TypeInferenceHelper.isTooComplexTooAnalyze(block)) {
      myHolder.createWeakWarningAnnotation(nameIdentifier,
                                           GroovyBundle.message("method.0.is.too.complex.too.analyze", method.getName()));
    }

    final PsiClass containingClass = method.getContainingClass();
    if (method.isConstructor()) {
      if (containingClass instanceof GrAnonymousClassDefinition) {
        myHolder.createErrorAnnotation(nameIdentifier, GroovyBundle.message("constructors.are.not.allowed.in.anonymous.class"));
      }
      else if (containingClass != null && containingClass.isInterface()) {
        myHolder.createErrorAnnotation(nameIdentifier, GroovyBundle.message("constructors.are.not.allowed.in.interface"));
      }
    }

    if (!method.hasModifierProperty(ABSTRACT) && method.getBlock() == null && !method.hasModifierProperty(NATIVE)) {
      final Annotation annotation =
        myHolder.createErrorAnnotation(nameIdentifier, GroovyBundle.message("not.abstract.method.should.have.body"));
      annotation.registerFix(new AddMethodBodyFix(method));
    }

    checkOverridingMethod(myHolder, method);
  }

  private static void checkOverridingMethod(@NotNull AnnotationHolder holder, @NotNull GrMethod method) {
    final List<HierarchicalMethodSignature> signatures = method.getHierarchicalMethodSignature().getSuperSignatures();

    for (HierarchicalMethodSignature signature : signatures) {
      final PsiMethod superMethod = signature.getMethod();
      if (superMethod.hasModifierProperty(FINAL)) {

        final String current = GroovyPresentationUtil.getSignaturePresentation(method.getSignature(PsiSubstitutor.EMPTY));
        final String superPresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
        final String superQName = getQNameOfMember(superMethod);

        holder.createErrorAnnotation(
          GrHighlightUtil.getMethodHeaderTextRange(method),
          GroovyBundle.message("method.0.cannot.override.method.1.in.2.overridden.method.is.final", current, superPresentation, superQName)
        );

        return;
      }

      final String currentModifier = VisibilityUtil.getVisibilityModifier(method.getModifierList());
      final String superModifier = VisibilityUtil.getVisibilityModifier(superMethod.getModifierList());

      if (PUBLIC.equals(superModifier) && (PROTECTED.equals(currentModifier) || PRIVATE.equals(currentModifier)) ||
          PROTECTED.equals(superModifier) && PRIVATE.equals(currentModifier)) {
        final String currentPresentation = GroovyPresentationUtil.getSignaturePresentation(method.getSignature(PsiSubstitutor.EMPTY));
        final String superPresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
        final String superQName = getQNameOfMember(superMethod);

        final PsiElement modifier = PsiUtil.findModifierInList(method.getModifierList(), currentModifier);
        holder.createErrorAnnotation(
          modifier != null? modifier : method.getNameIdentifierGroovy(),
          GroovyBundle.message("method.0.cannot.have.weaker.access.privileges.1.than.2.in.3.4", currentPresentation, currentModifier, superPresentation, superQName, superModifier)
        );
      }
    }
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

  private static void checkOptionalParametersInAbstractMethod(AnnotationHolder holder, GrMethod method) {
    if (!method.hasModifierProperty(ABSTRACT)) return;

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
  public void visitVariable(GrVariable variable) {
    checkName(variable);

    PsiElement parent = variable.getParent();
    if (parent instanceof GrForInClause) {
      PsiElement delimiter = ((GrForInClause)parent).getDelimiter();
      if (delimiter.getNode().getElementType() == GroovyTokenTypes.mCOLON) {
        GrTypeElement typeElement = variable.getTypeElementGroovy();
        GrModifierList modifierList = variable.getModifierList();
        if (typeElement == null && StringUtil.isEmptyOrSpaces(modifierList.getText())) {
          Annotation annotation = myHolder.createErrorAnnotation(variable.getNameIdentifierGroovy(), GroovyBundle
            .message("java.style.for.each.statement.requires.a.type.declaration"));
          annotation.registerFix(new ReplaceDelimiterFix());
        }
      }
    }


    PsiNamedElement duplicate = ResolveUtil.findDuplicate(variable);


    if (duplicate instanceof GrVariable &&
        (variable instanceof GrField || ResolveUtil.isScriptField(variable) || !(duplicate instanceof GrField))) {
      final String key = duplicate instanceof GrField ? "field.already.defined" : "variable.already.defined";
      myHolder.createErrorAnnotation(variable.getNameIdentifierGroovy(), GroovyBundle.message(key, variable.getName()));
    }

    PsiType type = variable.getDeclaredType();
    if (type instanceof PsiEllipsisType && !isLastParameter(variable)) {
      TextRange range = getEllipsisRange(variable);
      if (range == null) {
        range = getTypeRange(variable);
      }
      if (range != null) {
        myHolder.createErrorAnnotation(range, GroovyBundle.message("ellipsis.type.is.not.allowed.here"));
      }
    }
  }

  @Nullable
  private static TextRange getEllipsisRange(GrVariable variable) {
    if (variable instanceof GrParameter) {
      final PsiElement dots = ((GrParameter)variable).getEllipsisDots();
      if (dots != null) {
        return dots.getTextRange();
      }
    }
    return null;
  }

  @Nullable
  private static TextRange getTypeRange(GrVariable variable) {
    GrTypeElement typeElement = variable.getTypeElementGroovy();
    if (typeElement == null) return null;

    PsiElement sibling = typeElement.getNextSibling();
    if (sibling != null && sibling.getNode().getElementType() == GroovyTokenTypes.mTRIPLE_DOT) {
      return new TextRange(typeElement.getTextRange().getStartOffset(), sibling.getTextRange().getEndOffset());
    }

    return typeElement.getTextRange();
  }


  private static boolean isLastParameter(PsiVariable variable) {
    if (!(variable instanceof PsiParameter)) return false;

    PsiElement parent = variable.getParent();
    if (!(parent instanceof PsiParameterList)) return false;

    PsiParameter[] parameters = ((PsiParameterList)parent).getParameters();

    return parameters.length > 0 && parameters[parameters.length - 1] == variable;
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
  public void visitTypeParameterList(GrTypeParameterList list) {
    final PsiElement parent = list.getParent();
    if (parent instanceof GrMethod && ((GrMethod)parent).isConstructor() ||
        parent instanceof GrEnumTypeDefinition ||
        parent instanceof GrAnnotationTypeDefinition) {
      myHolder.createErrorAnnotation(list, GroovyBundle.message("type.parameters.are.unexpected"));
    }
  }

  @Override
  public void visitListOrMap(GrListOrMap listOrMap) {
    final PsiReference constructorReference = listOrMap.getReference();
    if (constructorReference != null) {
      final PsiElement startToken = listOrMap.getFirstChild();
      if (startToken != null && startToken.getNode().getElementType() == GroovyTokenTypes.mLBRACK) {
        myHolder.createInfoAnnotation(startToken, null).setTextAttributes(LITERAL_CONVERSION);
      }
      final PsiElement endToken = listOrMap.getLastChild();
      if (endToken != null && endToken.getNode().getElementType() == GroovyTokenTypes.mRBRACK) {
        myHolder.createInfoAnnotation(endToken, null).setTextAttributes(LITERAL_CONVERSION);
      }
    }

    final GrNamedArgument[] namedArguments = listOrMap.getNamedArguments();
    final GrExpression[] expressionArguments = listOrMap.getInitializers();

    if (namedArguments.length != 0 && expressionArguments.length != 0) {
      myHolder.createErrorAnnotation(listOrMap, GroovyBundle.message("collection.literal.contains.named.argument.and.expression.items"));
    }

    checkNamedArgs(namedArguments, false);
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

  @Override
  public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
    PsiElement resolved = refElement.resolve();
    if (resolved instanceof PsiClass &&
        (((PsiClass)resolved).isAnnotationType() ||
                                         GrAnnotationCollector.findAnnotationCollector((PsiClass)resolved) != null &&
                                         refElement.getParent() instanceof GrAnnotation)) {
      myHolder.createInfoAnnotation(refElement, null).setTextAttributes(ANNOTATION);
    }
  }

  @Override
  public void visitTypeElement(GrTypeElement typeElement) {
    final PsiElement parent = typeElement.getParent();
    if (!(parent instanceof GrMethod)) return;

    if (parent instanceof GrAnnotationMethod) {
      checkAnnotationAttributeType(typeElement, myHolder);
    }
    else if (((GrMethod)parent).isConstructor()) {
      myHolder.createErrorAnnotation(typeElement, GroovyBundle.message("constructors.cannot.have.return.type"));
    }
    else {
      checkMethodReturnType(((GrMethod)parent), typeElement, myHolder);
    }
  }

  @Override
  public void visitModifierList(GrModifierList modifierList) {
    final PsiElement parent = modifierList.getParent();
    if (parent instanceof GrMethod) {
      checkMethodDefinitionModifiers(myHolder, (GrMethod)parent);
    }
    else if (parent instanceof GrTypeDefinition) {
      checkTypeDefinitionModifiers(myHolder, (GrTypeDefinition)parent);
    }
    else if (parent instanceof GrVariableDeclaration && parent.getParent() instanceof GrTypeDefinition) {
      checkFieldModifiers(myHolder, (GrVariableDeclaration)parent);
    }
    else if (parent instanceof GrClassInitializer) {
      checkClassInitializerModifiers(myHolder, modifierList);
    }
  }

  private static void checkClassInitializerModifiers(AnnotationHolder holder, GrModifierList modifierList) {
    for (GrAnnotation annotation : modifierList.getAnnotations()) {
      holder.createErrorAnnotation(annotation, GroovyBundle.message("initializer.cannot.have.annotations"));
    }

    for (@GrModifier.GrModifierConstant String modifier : GrModifier.GROOVY_MODIFIERS) {
      if (STATIC.equals(modifier)) continue;
      checkModifierIsNotAllowed(modifierList, modifier, GroovyBundle.message("initializer.cannot.be.0", modifier), holder);
    }
  }

  @Override
  public void visitClassInitializer(GrClassInitializer initializer) {
    final PsiClass aClass = initializer.getContainingClass();
    if (aClass != null && aClass.isInterface()) {
      final TextRange range = GrHighlightUtil.getInitializerHeaderTextRange(initializer);
      myHolder.createErrorAnnotation(range, GroovyBundle.message("initializers.are.not.allowed.in.interface"));
    }
  }

  private static void checkFieldModifiers(AnnotationHolder holder, GrVariableDeclaration fieldDeclaration) {
    final GrModifierList modifierList = fieldDeclaration.getModifierList();
    final GrField member = (GrField)fieldDeclaration.getVariables()[0];

    checkAccessModifiers(holder, modifierList, member);
    checkDuplicateModifiers(holder, modifierList, member);

    if (modifierList.hasExplicitModifier(VOLATILE) && modifierList.hasExplicitModifier(FINAL)) {
      final Annotation annotation =
        holder.createErrorAnnotation(modifierList, GroovyBundle.message("illegal.combination.of.modifiers.volatile.and.final"));
      annotation.registerFix(new GrModifierFix(member, modifierList, VOLATILE, true, false));
      annotation.registerFix(new GrModifierFix(member, modifierList, FINAL, true, false));
    }

    checkModifierIsNotAllowed(modifierList, NATIVE, GroovyBundle.message("variable.cannot.be.native"), holder);
    checkModifierIsNotAllowed(modifierList, ABSTRACT, GroovyBundle.message("variable.cannot.be.abstract"), holder);

    if (member.getContainingClass() instanceof GrInterfaceDefinition) {
      checkModifierIsNotAllowed(modifierList, PRIVATE, GroovyBundle.message("interface.members.are.not.allowed.to.be", PRIVATE), holder);
      checkModifierIsNotAllowed(modifierList, PROTECTED, GroovyBundle.message("interface.members.are.not.allowed.to.be", PROTECTED),
                                holder);
    }
  }

  private static void checkModifierIsNotAllowed(@NotNull GrModifierList modifierList,
                                                @NotNull @GrModifier.GrModifierConstant String modifier,
                                                @Nullable String message,
                                                @NotNull AnnotationHolder holder) {
    checkModifierIsNotAllowedImpl(modifierList, modifier, message, holder, false);
  }

  private static void checkModifierIsNotAllowedImpl(@NotNull GrModifierList modifierList,
                                                    @NotNull @GrModifier.GrModifierConstant String modifier,
                                                    @Nullable String message,
                                                    @NotNull AnnotationHolder holder,
                                                    final boolean explicit) {
    if (explicit ? modifierList.hasModifierProperty(modifier) : modifierList.hasExplicitModifier(modifier)) {
      PsiElement toHighlight = PsiUtil.findModifierInList(modifierList, modifier);
      if (toHighlight == null) toHighlight = modifierList;
      final Annotation annotation = holder.createErrorAnnotation(toHighlight, message);
      annotation.registerFix(new GrModifierFix((PsiMember)modifierList.getParent(), modifierList, modifier, true, false));
    }
  }

  private static void checkAnnotationAttributeType(GrTypeElement element, AnnotationHolder holder) {
    if (element instanceof GrBuiltInTypeElement) return;

    if (element instanceof GrArrayTypeElement) {
      checkAnnotationAttributeType(((GrArrayTypeElement)element).getComponentTypeElement(), holder);
      return;
    }
    else if (element instanceof GrClassTypeElement) {
      final PsiElement resolved = ((GrClassTypeElement)element).getReferenceElement().resolve();
      if (resolved instanceof PsiClass) {
        if (CommonClassNames.JAVA_LANG_STRING.equals(((PsiClass)resolved).getQualifiedName())) return;
        if (CommonClassNames.JAVA_LANG_CLASS.equals(((PsiClass)resolved).getQualifiedName())) return;
        if (((PsiClass)resolved).isAnnotationType()) return;
        if (((PsiClass)resolved).isEnum()) return;
      }
    }

    holder.createErrorAnnotation(element, GroovyBundle.message("unexpected.attribute.type.0", element.getType()));
  }

  static void checkMethodReturnType(PsiMethod method, PsiElement toHighlight, AnnotationHolder holder) {
    final HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
    final List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();

    PsiType returnType = signature.getSubstitutor().substitute(method.getReturnType());

    for (HierarchicalMethodSignature superMethodSignature : superSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiType declaredReturnType = superMethod.getReturnType();
      PsiType superReturnType = superMethodSignature.getSubstitutor().substitute(declaredReturnType);
      if (superReturnType == PsiType.VOID && method instanceof GrMethod && ((GrMethod)method).getReturnTypeElementGroovy() == null) return;
      if (superMethodSignature.isRaw()) superReturnType = TypeConversionUtil.erasure(declaredReturnType);
      if (returnType == null || superReturnType == null || method == superMethod) continue;
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      String highlightInfo = checkSuperMethodSignature(superMethod, superMethodSignature, superReturnType, method, signature, returnType);
      if (highlightInfo != null) {
        holder.createErrorAnnotation(toHighlight, highlightInfo);
        return;
      }
    }
  }

  @Nullable
  private static String checkSuperMethodSignature(@NotNull PsiMethod superMethod,
                                                  @NotNull MethodSignatureBackedByPsiMethod superMethodSignature,
                                                  @NotNull PsiType superReturnType,
                                                  @NotNull PsiMethod method,
                                                  @NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                  @NotNull PsiType returnType) {
    PsiType substitutedSuperReturnType = substituteSuperReturnType(superMethodSignature, methodSignature, superReturnType);

    if (returnType.equals(substitutedSuperReturnType)) return null;

    final PsiType rawReturnType = TypeConversionUtil.erasure(returnType);
    final PsiType rawSuperReturnType = TypeConversionUtil.erasure(substitutedSuperReturnType);

    if (returnType instanceof PsiClassType && substitutedSuperReturnType instanceof PsiClassType) {
      if (TypeConversionUtil.isAssignable(rawSuperReturnType, rawReturnType)) {
        return null;
      }
    }
    else if (returnType instanceof PsiArrayType && superReturnType instanceof PsiArrayType) {
      if (rawReturnType.equals(rawSuperReturnType)) {
        return null;
      }
    }

    String qName = getQNameOfMember(method);
    String baseQName = getQNameOfMember(superMethod);
    final String presentation = returnType.getCanonicalText() + " " + GroovyPresentationUtil.getSignaturePresentation(methodSignature);
    final String basePresentation =
      superReturnType.getCanonicalText() + " " + GroovyPresentationUtil.getSignaturePresentation(superMethodSignature);
    return GroovyBundle.message("return.type.is.incompatible", presentation, qName, basePresentation, baseQName);
  }

  @NotNull
  private static PsiType substituteSuperReturnType(@NotNull MethodSignatureBackedByPsiMethod superMethodSignature,
                                                   @NotNull MethodSignatureBackedByPsiMethod methodSignature,
                                                   @NotNull PsiType superReturnType) {
    PsiType substitutedSuperReturnType;
    if (!superMethodSignature.isRaw() && superMethodSignature.equals(methodSignature)) { //see 8.4.5
      PsiSubstitutor unifyingSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature,
                                                                                                  superMethodSignature);
      substitutedSuperReturnType = unifyingSubstitutor == null
                                   ? superReturnType
                                   : unifyingSubstitutor.substitute(superMethodSignature.getSubstitutor().substitute(superReturnType));
    }
    else {
      substitutedSuperReturnType = TypeConversionUtil.erasure(superReturnType);
    }
    return substitutedSuperReturnType;
  }

  @NotNull
  private static String getQNameOfMember(@NotNull PsiMember member) {
    final PsiClass aClass = member.getContainingClass();
    return getQName(aClass);
  }

  @NotNull
  private static String getQName(@Nullable PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      return GroovyBundle.message("anonymous.class.derived.from.0", ((PsiAnonymousClass)aClass).getBaseClassType().getCanonicalText());
    }
    if (aClass != null) {
      final String qname = aClass.getQualifiedName();
      if (qname != null) {
        return qname;
      }
    }
    return "<null>";
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
        myHolder.createInfoAnnotation(label, null).setTextAttributes(MAP_KEY);
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

    final PsiElement element = refElement.resolve();
    if (element instanceof PsiClass) {
      PsiClass clazz = (PsiClass)element;
      if (clazz.hasModifierProperty(ABSTRACT)) {
        if (newExpression.getAnonymousClassDefinition() == null) {
          String message = clazz.isInterface()
                           ? GroovyBundle.message("cannot.instantiate.interface", clazz.getName())
                           : GroovyBundle.message("cannot.instantiate.abstract.class", clazz.getName());
          myHolder.createErrorAnnotation(refElement, message);
        }
        return;
      }
      if (newExpression.getQualifier() != null) {
        if (clazz.hasModifierProperty(STATIC)) {
          myHolder.createErrorAnnotation(newExpression, GroovyBundle.message("qualified.new.of.static.class"));
        }
      }
    }
  }

  private static boolean checkDiamonds(GrCodeReferenceElement refElement, AnnotationHolder holder) {
    GrTypeArgumentList typeArgumentList = refElement.getTypeArgumentList();
    if (typeArgumentList == null) return true;

    if (!typeArgumentList.isDiamond()) return true;

    final GroovyConfigUtils configUtils = GroovyConfigUtils.getInstance();
    if (!configUtils.isVersionAtLeast(refElement, GroovyConfigUtils.GROOVY1_8)) {
      final String message = GroovyBundle.message("diamonds.are.not.allowed.in.groovy.0", configUtils.getSDKVersion(refElement));
      holder.createErrorAnnotation(typeArgumentList, message);
    }
    return false;
  }

  @Override
  public void visitArgumentList(GrArgumentList list) {
    checkNamedArgs(list.getNamedArguments(), true);
  }

  @Override
  public void visitConstructorInvocation(GrConstructorInvocation invocation) {
    final GroovyResolveResult resolveResult = invocation.advancedResolve();
    if (resolveResult.getElement() == null) {
      final GroovyResolveResult[] results = invocation.multiResolve(false);
      final GrArgumentList argList = invocation.getArgumentList();
      if (results.length > 0) {
        String message = GroovyBundle.message("ambiguous.constructor.call");
        myHolder.createWarningAnnotation(argList, message);
      }
      else {
        final PsiClass clazz = invocation.getDelegatedClass();
        if (clazz != null) {
          //default constructor invocation
          PsiType[] argumentTypes = PsiUtil.getArgumentTypes(invocation.getInvokedExpression(), true);
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
    final GrModifierList modifierList = packageDefinition.getAnnotationList();
    checkAnnotationList(myHolder, modifierList, GroovyBundle.message("package.definition.cannot.have.modifiers"));
  }

  @Override
  public void visitClosure(GrClosableBlock closure) {
    super.visitClosure(closure);
    if (!closure.hasParametersSection() && !followsError(closure) && isClosureAmbiguous(closure)) {
      myHolder.createErrorAnnotation(closure, GroovyBundle.message("ambiguous.code.block"));
    }

    if (TypeInferenceHelper.isTooComplexTooAnalyze(closure)) {
      int startOffset = closure.getTextRange().getStartOffset();
      int endOffset;
      PsiElement arrow = closure.getArrow();
      if (arrow != null) {
        endOffset = arrow.getTextRange().getEndOffset();
      }
      else {
        Document document = PsiDocumentManager.getInstance(closure.getProject()).getDocument(closure.getContainingFile());
        if (document == null) return;
        String text = document.getText();
        endOffset = Math.min(closure.getTextRange().getEndOffset(), text.indexOf('\n', startOffset));
      }
      myHolder
        .createWeakWarningAnnotation(new TextRange(startOffset, endOffset), GroovyBundle.message("closure.is.too.complex.to.analyze"));
    }
  }

  /**
   * for example if (!(a inst)) {}
   * ^
   * we are here
   */

  private static boolean followsError(GrClosableBlock closure) {
    PsiElement prev = closure.getPrevSibling();
    return prev instanceof PsiErrorElement || prev instanceof PsiWhiteSpace && prev.getPrevSibling() instanceof PsiErrorElement;
  }

  private static boolean isClosureAmbiguous(GrClosableBlock closure) {
    PsiElement place = closure;
    while (true) {
      PsiElement parent = place.getParent();
      if (parent == null || parent instanceof GrUnAmbiguousClosureContainer) return false;

      if (PsiUtil.isExpressionStatement(place)) return true;
      if (parent.getFirstChild() != place) return false;
      place = parent;
    }
  }

  @Override
  public void visitLiteralExpression(GrLiteral literal) {
    final IElementType elementType = literal.getFirstChild().getNode().getElementType();
    if (elementType == GroovyTokenTypes.mSTRING_LITERAL || elementType == GroovyTokenTypes.mGSTRING_LITERAL) {
      checkStringLiteral(literal);
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
      //noinspection ConstantConditions
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
    for (GrStringContent part : gstring.getContents()) {
      final String text = part.getText();
      if (!GrStringUtil.parseStringCharacters(text, new StringBuilder(text.length()), null)) {
        myHolder.createErrorAnnotation(part, GroovyBundle.message("illegal.escape.character.in.string.literal"));
        return;
      }
    }

  }

  @Override
  public void visitGStringInjection(GrStringInjection injection) {
    if (((GrString)injection.getParent()).isPlainString()) {
      if (StringUtil.indexOf(injection.getText(), '\n') != -1) {
        myHolder.createErrorAnnotation(injection, GroovyBundle.message("injection.should.not.contain.line.feeds"));
      }
    }
  }

  private void checkStringLiteral(PsiElement literal) {
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(literal.getProject());
    String text;
    if (injectedLanguageManager.isInjectedFragment(literal.getContainingFile())) {
      text = injectedLanguageManager.getUnescapedText(literal);
    }
    else {
      text = literal.getText();
    }
    assert text != null;

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
      if (FINAL.equals(modifierText)) continue;
      if (GrModifier.DEF.equals(modifierText)) continue;
      myHolder.createErrorAnnotation(modifier, GroovyBundle.message("not.allowed.modifier.in.forin", modifierText));
    }
  }

  @Override
  public void visitFile(GroovyFileBase file) {
    final PsiClass scriptClass = file.getScriptClass();
    if (scriptClass != null) {
      checkDuplicateMethod(scriptClass, myHolder);
      checkSameNameMethodsWithDifferentAccessModifiers(myHolder, file.getCodeMethods());
    }
  }


  public void visitAnnotation(GrAnnotation annotation) {
    final GrCodeReferenceElement ref = annotation.getClassReference();
    final PsiElement resolved = ref.resolve();

    if (resolved == null) return;
    assert resolved instanceof PsiClass;

    PsiClass anno = (PsiClass)resolved;
    String qname = anno.getQualifiedName();
    if (!anno.isAnnotationType() && GrAnnotationCollector.findAnnotationCollector(anno) == null) {
      if (qname != null) {
        myHolder.createErrorAnnotation(ref, GroovyBundle.message("class.is.not.annotation", qname));
      }
      return;
    }

    for (CustomAnnotationChecker checker : CustomAnnotationChecker.EP_NAME.getExtensions()) {
      if (checker.checkApplicability(myHolder, annotation)) return;
    }

    String description = CustomAnnotationChecker.isAnnotationApplicable(annotation, annotation.getParent());
    if (description != null) {
      myHolder.createErrorAnnotation(ref, description);
    }
  }

  @Override
  public void visitAnnotationArgumentList(GrAnnotationArgumentList annotationArgumentList) {
    final GrAnnotation annotation = (GrAnnotation)annotationArgumentList.getParent();

    final PsiClass anno = ResolveUtil.resolveAnnotation(annotationArgumentList);
    if (anno == null) return;

    for (CustomAnnotationChecker checker : CustomAnnotationChecker.EP_NAME.getExtensions()) {
      if (checker.checkArgumentList(myHolder, annotation)) return;
    }

    Map<PsiElement, String> errors = ContainerUtil.newHashMap();
    CustomAnnotationChecker.checkAnnotationArguments(errors, anno, annotation.getClassReference(), annotationArgumentList.getAttributes(), true);
    for (Map.Entry<PsiElement, String> entry : errors.entrySet()) {
      myHolder.createErrorAnnotation(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void visitDefaultAnnotationValue(GrDefaultAnnotationValue defaultAnnotationValue) {
    final GrAnnotationMemberValue value = defaultAnnotationValue.getDefaultValue();
    if (value == null) return;

    final PsiElement parent = defaultAnnotationValue.getParent();
    assert parent instanceof GrAnnotationMethod;
    final PsiType type = ((GrAnnotationMethod)parent).getReturnType();

    Map<PsiElement, String> errors = ContainerUtil.newHashMap();
    CustomAnnotationChecker.checkAnnotationValueByType(errors, value, type, false);
    for (Map.Entry<PsiElement, String> entry : errors.entrySet()) {
      myHolder.createErrorAnnotation(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void visitAnnotationNameValuePair(GrAnnotationNameValuePair nameValuePair) {
    final PsiElement identifier = nameValuePair.getNameIdentifierGroovy();
    if (identifier == null) {
      final PsiElement parent = nameValuePair.getParent();
      if (parent instanceof GrAnnotationArgumentList) {
        final int count = ((GrAnnotationArgumentList)parent).getAttributes().length;
        if (count > 1) {
          myHolder.createErrorAnnotation(nameValuePair, GroovyBundle.message("attribute.name.expected"));
        }
      }
    }

    final GrAnnotationMemberValue value = nameValuePair.getValue();

    checkAnnotationAttributeValue(value, value);
  }

  private boolean checkAnnotationAttributeValue(GrAnnotationMemberValue value, PsiElement toHighlight) {
    if (value instanceof GrLiteral) return false;
    if (value instanceof GrClosableBlock) return false;
    if (value instanceof GrAnnotation) return false;

    if (value instanceof GrReferenceExpression) {
      PsiElement resolved = ((GrReferenceExpression)value).resolve();
      if (resolved instanceof PsiClass) return false;
      if (resolved instanceof PsiEnumConstant) return false;
      if (resolved == null && isClassReference(value)) return false;

      if (resolved instanceof GrAccessorMethod) resolved = ((GrAccessorMethod)resolved).getProperty();
      if (resolved instanceof PsiField) {
        GrExpression initializer;
        try {
          if (resolved instanceof GrField) {
            initializer = ((GrField)resolved).getInitializerGroovy();
          }
          else {
            final PsiExpression _initializer = ((PsiField)resolved).getInitializer();
            initializer = _initializer != null
                          ? (GrExpression)ExpressionConverter.getExpression(_initializer, GroovyFileType.GROOVY_LANGUAGE, value.getProject())
                          : null;
          }
        }
        catch (IncorrectOperationException e) {
          initializer = null;
        }

        if (initializer != null) {
          return checkAnnotationAttributeValue(initializer, toHighlight);
        }
      }
    }
    if (value instanceof GrAnnotationArrayInitializer) {
      for (GrAnnotationMemberValue expression : ((GrAnnotationArrayInitializer)value).getInitializers()) {
        if (checkAnnotationAttributeValue(expression, toHighlight)) return true;
      }
      return false;
    }

    myHolder.createErrorAnnotation(toHighlight, GroovyBundle.message("expected.0.to.be.inline.constant", value.getText()));
    return true;
  }

  private static boolean isClassReference(GrAnnotationMemberValue value) {
    if (value instanceof GrReferenceExpression) {
      final String referenceName = ((GrReferenceExpression)value).getReferenceName();
      if ("class".equals(referenceName)) {
        final GrExpression qualifier = ((GrReferenceExpression)value).getQualifier();
        if (qualifier instanceof GrReferenceExpression) {
          final PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
          if (resolved instanceof PsiClass) {
            return true;
          }
        }
      }
    }

    return false;
  }

  @Override
  public void visitImportStatement(GrImportStatement importStatement) {
    checkAnnotationList(myHolder, importStatement.getAnnotationList(), GroovyBundle.message("import.statement.cannot.have.modifiers"));
  }

  @Override
  public void visitExtendsClause(GrExtendsClause extendsClause) {
    GrTypeDefinition typeDefinition = (GrTypeDefinition)extendsClause.getParent();

    if (typeDefinition.isAnnotationType()) {
      myHolder.createErrorAnnotation(extendsClause, GroovyBundle.message("annotation.types.may.not.have.extends.clause"));
    }
    else if (typeDefinition.isInterface()) {
      checkReferenceList(myHolder, extendsClause, true, GroovyBundle.message("no.class.expected.here"), null);
    }
    else if (typeDefinition.isEnum()) {
      myHolder.createErrorAnnotation(extendsClause, GroovyBundle.message("enums.may.not.have.extends.clause"));
    }
    else {
      checkReferenceList(myHolder, extendsClause, false, GroovyBundle.message("no.interface.expected.here"),
                         new ChangeExtendsImplementsQuickFix(typeDefinition));
      checkForWildCards(myHolder, extendsClause);
    }

  }

  @Override
  public void visitImplementsClause(GrImplementsClause implementsClause) {
    GrTypeDefinition typeDefinition = (GrTypeDefinition)implementsClause.getParent();

    if (typeDefinition.isAnnotationType()) {
      myHolder.createErrorAnnotation(implementsClause, GroovyBundle.message("annotation.types.may.not.have.implements.clause"));
    }
    else if (typeDefinition.isInterface()) {
      myHolder.createErrorAnnotation(implementsClause, GroovyBundle.message("no.implements.clause.allowed.for.interface"))
        .registerFix(new ChangeExtendsImplementsQuickFix(typeDefinition));
    }
    else {
      checkReferenceList(myHolder, implementsClause, true, GroovyBundle.message("no.class.expected.here"),
                         new ChangeExtendsImplementsQuickFix(typeDefinition));
      checkForWildCards(myHolder, implementsClause);
    }
  }

  private static void checkReferenceList(@NotNull AnnotationHolder holder,
                                         @NotNull GrReferenceList list,
                                         boolean interfaceExpected,
                                         @NotNull String message,
                                         @Nullable IntentionAction fix) {
    for (GrCodeReferenceElement refElement : list.getReferenceElementsGroovy()) {
      final PsiElement psiClass = refElement.resolve();
      if (psiClass instanceof PsiClass && ((PsiClass)psiClass).isInterface() != interfaceExpected) {
        if (fix != null) {
          holder.createErrorAnnotation(refElement, message).registerFix(fix);
        }
      }
    }
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

  private static void checkThisOrSuperReferenceExpression(GrReferenceExpression ref, AnnotationHolder holder) {
    PsiElement nameElement = ref.getReferenceNameElement();
    if (nameElement == null) return;

    IElementType elementType = nameElement.getNode().getElementType();
    if (!(elementType == GroovyTokenTypes.kSUPER || elementType == GroovyTokenTypes.kTHIS)) return;

    final GrExpression qualifier = ref.getQualifier();
    if (qualifier instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)qualifier).resolve();
      if (resolved instanceof PsiClass) {

        GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(ref, GrTypeDefinition.class, true, GroovyFile.class);
        if (containingClass == null || containingClass.getContainingClass() == null && !containingClass.isAnonymous()) {
          holder.createErrorAnnotation(ref, GroovyBundle.message("qualified.0.is.allowed.only.in.nested.or.inner.classes",
                                                                 nameElement.getText()));
          return;
        }

        if (PsiTreeUtil.isAncestor(resolved, ref, true)) {
          if (PsiUtil.hasEnclosingInstanceInScope((PsiClass)resolved, ref, true)) {
            holder.createInfoAnnotation(nameElement, null).setTextAttributes(KEYWORD);
          }
        }
        else {
          String qname = ((PsiClass)resolved).getQualifiedName();
          assert qname != null;
          holder.createErrorAnnotation(ref, GroovyBundle.message("is.not.enclosing.class", qname));
        }
      }
    }
    else if (qualifier == null) {
      if (elementType == GroovyTokenTypes.kSUPER) {
        final GrMember container = PsiTreeUtil.getParentOfType(ref, GrMethod.class, GrClassInitializer.class);
        if (container != null && container.hasModifierProperty(STATIC)) {
          holder.createErrorAnnotation(ref, GroovyBundle.message("super.cannot.be.used.in.static.context"));
        }
      }
    }
  }

  private static void checkGrDocReferenceElement(AnnotationHolder holder, PsiElement element) {
    ASTNode node = element.getNode();
    if (node != null && TokenSets.BUILT_IN_TYPES.contains(node.getElementType())) {
      Annotation annotation = holder.createInfoAnnotation(element, null);
      annotation.setTextAttributes(KEYWORD);
    }
  }

  private static void checkAnnotationList(AnnotationHolder holder, @NotNull GrModifierList modifierList, String message) {
    final PsiElement[] modifiers = modifierList.getModifiers();
    for (PsiElement modifier : modifiers) {
      if (!(modifier instanceof PsiAnnotation)) {
        holder.createErrorAnnotation(modifier, message);
      }
    }
  }

  private static void checkImplementedMethodsOfClass(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.hasModifierProperty(ABSTRACT)) return;
    if (typeDefinition.isAnnotationType()) return;
    if (typeDefinition instanceof GrTypeParameter) return;

    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(typeDefinition);
    if (abstractMethod== null) return;

    String notImplementedMethodName = abstractMethod.getName();

    final TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
    final Annotation annotation = holder.createErrorAnnotation(range,
                                                               GroovyBundle.message("method.is.not.implemented", notImplementedMethodName));
    registerImplementsMethodsFix(typeDefinition, abstractMethod, annotation);
  }

  private static void registerImplementsMethodsFix(@NotNull GrTypeDefinition typeDefinition,
                                                   @NotNull PsiMethod abstractMethod,
                                                   @NotNull Annotation annotation) {
    if (!OverrideImplementExploreUtil.getMethodsToOverrideImplement(typeDefinition, true).isEmpty()) {
      annotation.registerFix(QuickFixFactory.getInstance().createImplementMethodsFix(typeDefinition));
    }

    if (!JavaPsiFacade.getInstance(typeDefinition.getProject()).getResolveHelper().isAccessible(abstractMethod, typeDefinition, null)) {
      annotation.registerFix(new GrModifierFix(abstractMethod, abstractMethod.getModifierList(), PUBLIC, true, true));
      annotation.registerFix(new GrModifierFix(abstractMethod, abstractMethod.getModifierList(), PROTECTED, true, true));
    }

    if (!(typeDefinition instanceof GrAnnotationTypeDefinition) && typeDefinition.getModifierList() != null) {
      annotation.registerFix(new GrModifierFix(typeDefinition, typeDefinition.getModifierList(), ABSTRACT, false, true));
    }
  }

  private static void checkInnerMethod(AnnotationHolder holder, GrMethod grMethod) {
    final PsiElement parent = grMethod.getParent();
    if (parent instanceof GrOpenBlock || parent instanceof GrClosableBlock) {
      holder.createErrorAnnotation(grMethod.getNameIdentifierGroovy(), GroovyBundle.message("Inner.methods.are.not.supported"));
    }
  }

  private static void registerMakeAbstractMethodNotAbstractFix(Annotation annotation, GrMethod method, boolean makeClassAbstract) {
    if (method.getBlock() == null) {
      annotation.registerFix(new AddMethodBodyFix(method));
    }
    else {
      annotation.registerFix(new DeleteMethodBodyFix(method));
    }
    annotation.registerFix(new GrModifierFix(method, method.getModifierList(), ABSTRACT, false, false));
    if (makeClassAbstract) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        final GrModifierList list = (GrModifierList)containingClass.getModifierList();
        if (list != null && !list.hasModifierProperty(ABSTRACT)) {
          annotation.registerFix(new GrModifierFix(containingClass, list, ABSTRACT, false, true));
        }
      }
    }
  }

  private static void checkMethodDefinitionModifiers(AnnotationHolder holder, GrMethod method) {
    final GrModifierList modifiersList = method.getModifierList();
    checkAccessModifiers(holder, modifiersList, method);
    checkDuplicateModifiers(holder, modifiersList, method);
    checkOverrideAnnotation(holder, modifiersList, method);

    checkModifierIsNotAllowed(modifiersList, VOLATILE, GroovyBundle.message("method.has.incorrect.modifier.volatile"), holder);

    if (method.hasModifierProperty(FINAL) && method.hasModifierProperty(ABSTRACT)) {
      final Annotation annotation =
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.abstract.and.final"));
      annotation.registerFix(new GrModifierFix(method, modifiersList, FINAL, false, false));
      annotation.registerFix(new GrModifierFix(method, modifiersList, ABSTRACT, false, false));
    }

    //script methods
    boolean isMethodAbstract = modifiersList.hasExplicitModifier(ABSTRACT);
    if (method.getParent() instanceof GroovyFileBase) {
      if (isMethodAbstract) {
        final Annotation annotation = holder.createErrorAnnotation(getModifierOrList(modifiersList, ABSTRACT),
                                                                   GroovyBundle.message("script.method.cannot.have.modifier.abstract"));
        registerMakeAbstractMethodNotAbstractFix(annotation, method, false);
      }

      checkModifierIsNotAllowed(modifiersList, NATIVE, GroovyBundle.message("script.cannot.have.modifier.native"), holder);
    }
    //type definition methods
    else if (method.getParent() != null && method.getParent().getParent() instanceof GrTypeDefinition) {
      GrTypeDefinition containingTypeDef = ((GrTypeDefinition)method.getParent().getParent());

      //interface
      if (containingTypeDef.isInterface()) {
        checkModifierIsNotAllowed(modifiersList, STATIC, GroovyBundle.message("interface.must.have.no.static.method"), holder);
        checkModifierIsNotAllowed(modifiersList, PRIVATE, GroovyBundle.message("interface.members.are.not.allowed.to.be", PRIVATE), holder);
        checkModifierIsNotAllowed(modifiersList, PROTECTED, GroovyBundle.message("interface.members.are.not.allowed.to.be", PROTECTED),
                                  holder);
      }
      else if (containingTypeDef.isAnonymous()) {
        if (isMethodAbstract) {
          final Annotation annotation = holder.createErrorAnnotation(getModifierOrList(modifiersList, ABSTRACT),
                                                                     GroovyBundle.message("anonymous.class.cannot.have.abstract.method"));
          registerMakeAbstractMethodNotAbstractFix(annotation, method, false);
        }
      }
      //class
      else {
        PsiModifierList typeDefModifiersList = containingTypeDef.getModifierList();
        LOG.assertTrue(typeDefModifiersList != null, "modifiers list must be not null");

        if (!typeDefModifiersList.hasModifierProperty(ABSTRACT) && isMethodAbstract) {
          final Annotation annotation =
            holder.createErrorAnnotation(modifiersList, GroovyBundle.message("only.abstract.class.can.have.abstract.method"));
          registerMakeAbstractMethodNotAbstractFix(annotation, method, true);
        }
      }

      if (method.isConstructor()) {
        checkModifierIsNotAllowed(modifiersList, STATIC, GroovyBundle.message("constructor.cannot.have.static.modifier"), holder);
      }
    }

    if (method.hasModifierProperty(NATIVE) && method.getBlock() != null) {
      final Annotation annotation = holder.createErrorAnnotation(getModifierOrList(modifiersList, NATIVE),
                                                                 GroovyBundle.message("native.methods.cannot.have.body"));
      annotation.registerFix(new GrModifierFix((PsiMember)modifiersList.getParent(), modifiersList, NATIVE, true, false));
      annotation.registerFix(new DeleteMethodBodyFix(method));
    }
  }

  @NotNull
  private static PsiElement getModifierOrList(@NotNull GrModifierList modifiersList, @GrModifier.GrModifierConstant final String modifier) {
    PsiElement m = PsiUtil.findModifierInList(modifiersList, modifier);
    return m != null ? m : modifiersList;
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

      if (psiClass != null && psiClass.hasModifierProperty(FINAL)) {
        final Annotation annotation =
          holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), GroovyBundle.message("final.class.cannot.be.extended"));
        annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, FINAL, false, false));
      }
    }

    if (!typeDefinition.isEnum() && modifiersList.hasModifierProperty(ABSTRACT) && modifiersList.hasModifierProperty(FINAL)) {
      final Annotation annotation =
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.abstract.and.final"));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, FINAL, false, false));
      annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, ABSTRACT, false, false));
    }

    checkModifierIsNotAllowed(modifiersList, TRANSIENT, GroovyBundle.message("modifier.transient.not.allowed.here"), holder);
    checkModifierIsNotAllowed(modifiersList, VOLATILE, GroovyBundle.message("modifier.volatile.not.allowed.here"), holder);

    /**** interface ****/
    if (typeDefinition.isInterface()) {
      checkModifierIsNotAllowed(modifiersList, FINAL, GroovyBundle.message("intarface.cannot.have.modifier.final"), holder);
    }
  }

  private static void checkDuplicateModifiers(AnnotationHolder holder, @NotNull GrModifierList list, PsiMember member) {
    final PsiElement[] modifiers = list.getModifiers();
    Set<String> set = new THashSet<String>(modifiers.length);
    for (PsiElement modifier : modifiers) {
      if (modifier instanceof GrAnnotation) continue;
      @GrModifier.GrModifierConstant String name = modifier.getText();
      if (set.contains(name)) {
        final Annotation annotation = holder.createErrorAnnotation(list, GroovyBundle.message("duplicate.modifier", name));
        annotation.registerFix(new GrModifierFix(member, list, name, false, false));
      }
      else {
        set.add(name);
      }
    }
  }

  private static void checkAccessModifiers(AnnotationHolder holder, @NotNull GrModifierList modifierList, PsiMember member) {
    boolean hasPrivate = modifierList.hasExplicitModifier(PRIVATE);
    boolean hasPublic = modifierList.hasExplicitModifier(PUBLIC);
    boolean hasProtected = modifierList.hasExplicitModifier(PROTECTED);

    if (hasPrivate && hasPublic || hasPrivate && hasProtected || hasPublic && hasProtected) {
      final Annotation annotation = holder.createErrorAnnotation(modifierList, GroovyBundle.message("illegal.combination.of.modifiers"));
      if (hasPrivate) {
        annotation.registerFix(new GrModifierFix(member, modifierList, PRIVATE, false, false));
      }
      if (hasProtected) {
        annotation.registerFix(new GrModifierFix(member, modifierList, PROTECTED, false, false));
      }
      if (hasPublic) {
        annotation.registerFix(new GrModifierFix(member, modifierList, PUBLIC, false, false));
      }
    }
    else if (member instanceof PsiMethod &&
             member.getContainingClass() instanceof GrInterfaceDefinition &&
             hasPublic &&
             !GroovyConfigUtils.getInstance().isVersionAtLeast(member, "1.8.4")) {
      final PsiElement publicModifier = ObjectUtils.assertNotNull(PsiUtil.findModifierInList(modifierList, PUBLIC));
      holder.createErrorAnnotation(publicModifier, GroovyBundle.message("public.modifier.is.not.allowed.in.interfaces"))
        .registerFix(new GrModifierFix(member, modifierList, PUBLIC, false, false));
    }
    else if (member instanceof PsiClass &&
             member.getContainingClass() == null &&
             GroovyConfigUtils.getInstance().isVersionAtLeast(member, GroovyConfigUtils.GROOVY2_0)) {
      checkModifierIsNotAllowed(modifierList, PRIVATE, GroovyBundle.message("top.level.class.maynot.have.private.modifier"), holder);
      checkModifierIsNotAllowed(modifierList, PROTECTED, GroovyBundle.message("top.level.class.maynot.have.protected.modifier"), holder);
    }
  }

  private static void checkDuplicateMethod(PsiClass clazz, AnnotationHolder holder) {
    MultiMap<MethodSignature, PsiMethod> map = GrClosureSignatureUtil.findRawMethodSignatures(clazz.getMethods(), clazz);
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
          holder.createErrorAnnotation(GrHighlightUtil.getMethodHeaderTextRange(method), GroovyBundle
            .message("method.duplicate", signaturePresentation, method.getContainingClass().getName()));
        }
      }
    }
  }

  private static void checkTypeDefinition(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    final GroovyConfigUtils configUtils = GroovyConfigUtils.getInstance();
    if (typeDefinition.isAnonymous()) {
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

    if (typeDefinition.isAnnotationType() && typeDefinition.getContainingClass() != null) {
      holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), GroovyBundle.message("annotation.type.cannot.be.inner"));
    }

    checkDuplicateClass(typeDefinition, holder);

    checkCyclicInheritance(holder, typeDefinition);
  }

  private static void checkCyclicInheritance(AnnotationHolder holder,
                                             GrTypeDefinition typeDefinition) {
    final PsiClass psiClass = getCircularClass(typeDefinition, new HashSet<PsiClass>());
    if (psiClass != null) {
      String qname = psiClass.getQualifiedName();
      assert qname != null;
      holder.createErrorAnnotation(GrHighlightUtil.getClassHeaderTextRange(typeDefinition),
                                   GroovyBundle.message("cyclic.inheritance.involving.0", qname));
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
          if (!CommonClassNames.JAVA_LANG_OBJECT.equals(((PsiClass)superType).getQualifiedName())) {
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
    final GrCodeReferenceElement[] elements = clause.getReferenceElementsGroovy();
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
    String name = typeDefinition.getName();
    if (containingClass != null) {
      final String containingClassName = containingClass.getName();
      if (containingClassName != null && containingClassName.equals(name)) {
        holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(),
                                     GroovyBundle.message("duplicate.inner.class", name));
      }
    }
    final String qName = typeDefinition.getQualifiedName();
    if (qName != null) {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(typeDefinition.getProject());
      final PsiClass[] classes = facade.findClasses(qName, typeDefinition.getResolveScope());
      if (classes.length > 1) {
        String packageName = getPackageName(typeDefinition);

        if (!isScriptGeneratedClass(classes)) {
          holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(),
                                       GroovyBundle.message("duplicate.class", name, packageName));
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
}

