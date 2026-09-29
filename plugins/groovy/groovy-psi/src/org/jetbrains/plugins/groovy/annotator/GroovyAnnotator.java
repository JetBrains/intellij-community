// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ClassUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.PsiTypeParameterListOwner;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.ExpressionConverter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.annotator.checkers.AnnotationChecker;
import org.jetbrains.plugins.groovy.annotator.checkers.CustomAnnotationChecker;
import org.jetbrains.plugins.groovy.annotator.checkers.GeneratedConstructorAnnotationChecker;
import org.jetbrains.plugins.groovy.annotator.intentions.ChangeExtendsImplementsQuickFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrMoveClassToCorrectPlaceFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrRemoveExceptionFix;
import org.jetbrains.plugins.groovy.annotator.intentions.GrReplacePrimitiveTypeWithWrapperFix;
import org.jetbrains.plugins.groovy.annotator.intentions.ReplaceDelimiterFix;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrModifierFix;
import org.jetbrains.plugins.groovy.codeInspection.bugs.GrRemoveModifierFix;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.GrBlockLambdaBody;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GrImportAlias;
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArrayInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLoopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParameterListOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrTryCatchStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnnotationTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrInterfaceDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrRecordDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTraitTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrArrayTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrDisjunctionTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrWildcardTypeArgument;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.FunctionalExpressionFlowUtil;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrAnnotationCollector;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GrClassImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;
import org.jetbrains.plugins.groovy.lang.resolve.ast.AffectedMembersCache;
import org.jetbrains.plugins.groovy.lang.resolve.ast.GrGeneratedConstructorUtils;
import org.jetbrains.plugins.groovy.lang.resolve.ast.InheritConstructorContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ast.TupleConstructorAttributes;
import org.jetbrains.plugins.groovy.transformations.immutable.GrImmutableUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.psi.util.PsiTreeUtil.findChildOfType;
import static org.jetbrains.plugins.groovy.annotator.ImplKt.checkInnerClassReferenceFromInstanceContext;
import static org.jetbrains.plugins.groovy.annotator.ImplKt.checkUnresolvedCodeReference;
import static org.jetbrains.plugins.groovy.annotator.StringInjectionKt.getLineFeed;
import static org.jetbrains.plugins.groovy.annotator.UtilKt.checkModifierIsNotAllowed;
import static org.jetbrains.plugins.groovy.annotator.UtilKt.checkVariableModifiers;
import static org.jetbrains.plugins.groovy.annotator.UtilKt.registerLocalFix;
import static org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GroovyUnresolvedAccessChecker.checkUnresolvedReference;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isInStaticCompilationContext;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.findScriptField;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isFieldDeclaration;

public final class GroovyAnnotator extends GroovyElementVisitor {
  private static final Logger LOG = Logger.getInstance(GroovyAnnotator.class);

  public static final Condition<PsiClass> IS_INTERFACE = aClass -> aClass.isInterface();
  private static final Condition<PsiClass> IS_NOT_INTERFACE = aClass -> !aClass.isInterface();
  public static final Condition<PsiClass> IS_TRAIT = aClass -> GrTraitUtil.isTrait(aClass);

  private final AnnotationHolder myHolder;

  public GroovyAnnotator(@NotNull AnnotationHolder holder) {
    myHolder = holder;
  }

  @Override
  public void visitTypeArgumentList(@NotNull GrTypeArgumentList typeArgumentList) {
    if (!(typeArgumentList.getParent() instanceof GrReferenceElement<?> ref)) return;

    final GroovyResolveResult resolveResult = ref.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();

    if (resolved == null) return;

    if (!(resolved instanceof PsiTypeParameterListOwner owner)) {
      myHolder.newAnnotation(HighlightSeverity.WARNING, GroovyBundle.message("type.argument.list.is.not.allowed.here")).create();
      return;
    }

    if (typeArgumentList.isDiamond()) return;

    final PsiTypeParameter[] parameters = owner.getTypeParameters();
    final GrTypeElement[] arguments = typeArgumentList.getTypeArgumentElements();

    if (arguments.length != parameters.length) {
      myHolder.newAnnotation(HighlightSeverity.WARNING,
                                       GroovyBundle.message("wrong.number.of.type.arguments", arguments.length, parameters.length)).create();
      return;
    }

    for (int i = 0; i < parameters.length; i++) {
      final PsiType argType = arguments[i].getType();
      for (PsiClassType superType : parameters[i].getExtendsListTypes()) {
        final PsiType substitutedSuper = substitutor.substitute(superType);
        if (substitutedSuper != null && !substitutedSuper.isAssignableFrom(argType)) {
          String message = GroovyBundle.message("type.argument.0.is.not.in.its.bound.should.extend.1", 
                                                argType.getCanonicalText(), superType.getCanonicalText());
          myHolder.newAnnotation(HighlightSeverity.WARNING, message).range(arguments[i]).create();
          break;
        }
      }
    }
  }

  @Override
  public void visitNamedArgument(@NotNull GrNamedArgument argument) {
    if (argument.getParent() instanceof GrArgumentList list && list.getParent() instanceof GrIndexProperty) {
      String message = GroovyBundle.message("named.arguments.are.not.allowed.inside.index.operations");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).create();
    }
  }

  @Override
  public void visitElement(@NotNull GroovyPsiElement element) {
    if (element.getParent() instanceof GrDocReferenceElement) {
      checkGrDocReferenceElement(myHolder, element);
    }
  }

  @Override
  public void visitTryStatement(@NotNull GrTryCatchStatement statement) {
    final GrCatchClause[] clauses = statement.getCatchClauses();

    if (statement.getResourceList() == null && clauses.length == 0 && statement.getFinallyClause() == null) {
      String message = GroovyBundle.message("try.without.catch.finally");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(statement.getFirstChild()).create();
      return;
    }

    List<PsiType> usedExceptions = new ArrayList<>();

    for (GrCatchClause clause : clauses) {
      final GrParameter parameter = clause.getParameter();
      if (parameter == null) continue;

      final GrTypeElement typeElement = parameter.getTypeElementGroovy();
      PsiType type = typeElement != null ? typeElement.getType() : TypesUtil.createType(CommonClassNames.JAVA_LANG_EXCEPTION, statement);

      if (typeElement instanceof GrDisjunctionTypeElement disjunction) {
        final GrTypeElement[] elements = disjunction.getTypeElements();
        final PsiType[] types = ContainerUtil.map2Array(elements, PsiType.class, GrTypeElement::getType);

        List<PsiType> usedInsideDisjunction = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
          if (checkExceptionUsed(usedExceptions, parameter, elements[i], types[i])) {
            usedInsideDisjunction.add(types[i]);
            for (int j = 0; j < types.length; j++) {
              if (i != j && types[j].isAssignableFrom(types[i])) {
                String message = GroovyBundle.message("unnecessary.type", types[i].getCanonicalText(), types[j].getCanonicalText());
                myHolder.newAnnotation(HighlightSeverity.WARNING, message).range(elements[i])
                  .withFix(new GrRemoveExceptionFix(true)).create();
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
  public void visitCatchClause(@NotNull GrCatchClause clause) {
    final GrParameter parameter = clause.getParameter();
    if (parameter == null) return;

    final GrTypeElement typeElement = parameter.getTypeElementGroovy();
    if (typeElement != null) {
      final PsiType type = typeElement.getType();
      if (type instanceof PsiClassType t && t.resolve() == null) return; //don't highlight unresolved types
      final PsiClassType throwable = TypesUtil.createType(CommonClassNames.JAVA_LANG_THROWABLE, clause);
      if (!throwable.isAssignableFrom(type)) {
        String message = GroovyBundle.message("catch.statement.parameter.type.should.be.a.subclass.of.throwable");
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(typeElement).create();
      }
    }
  }

  @Override
  public void visitDocComment(@NotNull GrDocComment comment) {
    String text = comment.getText();
    if (!text.endsWith("*/")) {
      TextRange range = comment.getTextRange();
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("doc.end.expected"))
        .range(new TextRange(range.getEndOffset() - 1, range.getEndOffset())).create();
    }
  }

  @Override
  public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
    if (!variableDeclaration.isTuple()) {
      GrTypeParameterList typeParameterList = findChildOfType(variableDeclaration, GrTypeParameterList.class);
      if (typeParameterList != null) {
        String message = GroovyBundle.message("type.parameters.are.unexpected");
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(typeParameterList).create();
      }
    }
    else {
      GrModifierList list = variableDeclaration.getModifierList();
      for (PsiElement modifier : list.getModifiers()) {
        String text = modifier.getText();
        if (PsiModifier.FINAL.equals(text)) {
          if (!list.hasModifierProperty(GrModifier.DEF) && !GroovyConfigUtils.isAtLeastGroovy30(list)) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("invalid.multiple.assignment")).range(modifier).create();
          }
        }
        else if (GrModifier.VAR.equals(text)) {
          if (!GroovyConfigUtils.isAtLeastGroovy30(list)) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.var.declaration")).range(modifier).create();
          }
        }
        else if (GrModifier.VAL.equals(text)) {
          if (!GroovyConfigUtils.isAtLeastGroovy60(list)) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("unsupported.val.declaration")).range(modifier).create();
          }
        }
      }
    }
  }

  private boolean checkExceptionUsed(List<PsiType> usedExceptions, GrParameter parameter, GrTypeElement typeElement, PsiType type) {
    for (PsiType exception : usedExceptions) {
      if (exception.isAssignableFrom(type)) {
        String message = GroovyBundle.message("exception.0.has.already.been.caught", type.getCanonicalText());
        myHolder.newAnnotation(HighlightSeverity.WARNING, message)
          .range(typeElement != null ? typeElement : parameter.getNameIdentifierGroovy())
          .withFix(new GrRemoveExceptionFix(parameter.getTypeElementGroovy() instanceof GrDisjunctionTypeElement)).create();
        return false;
      }
    }
    return true;
  }

  @Override
  public void visitReferenceExpression(@NotNull GrReferenceExpression referenceExpression) {
    checkStringNameIdentifier(referenceExpression);
    checkThisOrSuperReferenceExpression(referenceExpression, myHolder);
    checkFinalFieldAccess(referenceExpression);
    checkFinalParameterAccess(referenceExpression);

    if (ResolveUtil.isKeyOfMap(referenceExpression)) {
      PsiElement nameElement = referenceExpression.getReferenceNameElement();
      LOG.assertTrue(nameElement != null);
      myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(nameElement).textAttributes(GroovySyntaxHighlighter.MAP_KEY).create();
    }
    else if (ResolveUtil.isClassReference(referenceExpression)) {
      PsiElement nameElement = referenceExpression.getReferenceNameElement();
      LOG.assertTrue(nameElement != null);
      myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION)
        .range(nameElement).textAttributes(GroovySyntaxHighlighter.KEYWORD).create();
    }
    else if (isInStaticCompilationContext(referenceExpression)) {
      checkUnresolvedReference(referenceExpression, true, true, new UnresolvedReferenceAnnotatorSink(myHolder));
    }
  }

  private void checkFinalParameterAccess(GrReferenceExpression ref) {
    if (ref.resolve() instanceof GrParameter parameter
        && parameter.isPhysical()
        && parameter.hasModifierProperty(PsiModifier.FINAL)
        && PsiUtil.isLValue(ref) && parameter.getDeclarationScope() instanceof PsiMethod) {
      String message = GroovyBundle.message("cannot.assign.a.value.to.final.parameter.0", parameter.getName());
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).create();
    }
  }

  private void checkFinalFieldAccess(@NotNull GrReferenceExpression ref) {
    if (ref.resolve() instanceof GrField field && field.isPhysical() && field.hasModifierProperty(PsiModifier.FINAL)
        && PsiUtil.isLValue(ref)) {

      final PsiClass containingClass = field.getContainingClass();
      if (containingClass != null && PsiTreeUtil.isAncestor(containingClass, ref, true)) {
        GrMember container = GrHighlightUtil.findClassMemberContainer(ref, containingClass);

        if (field.hasModifierProperty(PsiModifier.STATIC)) {
          if (container instanceof GrClassInitializer initializer && initializer.isStatic()) {
            return;
          }
        }
        else {
          if (container instanceof GrMethod method && method.isConstructor() ||
              container instanceof GrClassInitializer initializer && !initializer.isStatic()) {
            return;
          }
        }

        AnnotationBuilder builder =
          myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("cannot.assign.a.value.to.final.field.0", field.getName()));
        Function<ProblemDescriptor, PsiModifierList> function =
          d -> d.getPsiElement() instanceof GrReferenceExpression r && r.resolve() instanceof GrVariable var ? var.getModifierList() : null;
        String message = GroovyBundle.message("change.modifier.not", ref.getReferenceName(), PsiModifier.FINAL);
        GrModifierFix fix = new GrModifierFix(message, PsiModifier.FINAL, false, function);
        registerLocalFix(builder, fix, ref, message, ProblemHighlightType.ERROR, ref.getTextRange());
        builder.create();
      }
    }
  }

  private void checkStringNameIdentifier(GrReferenceExpression ref) {
    final PsiElement nameElement = ref.getReferenceNameElement();
    if (nameElement == null) return;

    final IElementType elementType = nameElement.getNode().getElementType();
    if (GroovyTokenSets.STRING_LITERALS.contains(elementType)) {
      checkStringLiteral(nameElement);
    }
    else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      checkRegexLiteral(nameElement);
    }
  }

  @Override
  public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
    final PsiElement parent = typeDefinition.getParent();
    if (!(typeDefinition.isAnonymous() ||
          parent instanceof GrTypeDefinitionBody ||
          parent instanceof GroovyFile ||
          typeDefinition instanceof GrTypeParameter)) {
      final TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);

      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("class.definition.is.not.expected.here")).range(range)
        .withFix(new GrMoveClassToCorrectPlaceFix(typeDefinition)).create();
    }
    checkTypeDefinition(myHolder, typeDefinition);
    PsiElement identifier = typeDefinition.getNameIdentifierGroovy();
    String name = identifier.getText();
    if (GrModifier.VAR.equals(name) || GrModifier.VAL.equals(name)) {
      String message = GroovyBundle.message("0.is.a.restricted.type.name", name);
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(identifier.getTextRange()).create();
    }

    // enum constants are not handled here because their getTextOffset() is crazy - outside their own getTextRange()
    if (!(typeDefinition instanceof PsiEnumConstantInitializer)) {
      checkImplementedMethodsOfClass(myHolder, typeDefinition);
    }
    checkConstructors(myHolder, typeDefinition);
    checkAnnotationCollector(myHolder, typeDefinition);
    checkSameNameMethodsWithDifferentAccessModifiers(myHolder, typeDefinition.getCodeMethods());
    checkInheritorOfSelfTypes(myHolder, typeDefinition);
  }

  private static void checkInheritorOfSelfTypes(AnnotationHolder holder, GrTypeDefinition definition) {
    if (!(definition instanceof GrClassDefinition)) return;
    for (PsiClass selfClass : GrTraitUtil.getSelfTypeClasses(definition)) {
      if (InheritanceUtil.isInheritorOrSelf(definition, selfClass, true)) continue;
      String message = GroovyBundle.message("selfType.class.does.not.inherit", definition.getQualifiedName(), selfClass.getQualifiedName());
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(GrHighlightUtil.getClassHeaderTextRange(definition)).create();
      break;
    }
  }

  private static void checkSameNameMethodsWithDifferentAccessModifiers(AnnotationHolder holder, GrMethod[] methods) {
    MultiMap<String, GrMethod> map = MultiMap.create();
    for (GrMethod method : methods) {
      if (!method.isConstructor()) {
        map.putValue(method.getName(), method);
      }
    }

    for (Map.Entry<String, Collection<GrMethod>> entry : map.entrySet()) {
      Collection<GrMethod> collection = entry.getValue();
      if (collection.size() > 1 && !sameAccessModifier(collection)) {
        for (GrMethod method : collection) {
          String message = GroovyBundle.message("mixing.private.and.public.protected.methods.of.the.same.name");
          holder.newAnnotation(HighlightSeverity.ERROR, message).range(GrHighlightUtil.getMethodHeaderTextRange(method)).create();
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
      String message = GroovyBundle.message("annotation.collector.cannot.have.attributes");
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(definition.getNameIdentifierGroovy()).create();
    }
  }

  private static void checkConstructors(@NotNull AnnotationHolder holder, @NotNull GrTypeDefinition typeDefinition) {
    if (typeDefinition.isEnum() || typeDefinition.isInterface() || typeDefinition.isAnonymous()
        || typeDefinition instanceof GrTypeParameter) return;
    final PsiClass superClass = typeDefinition.getSuperClass();
    if (superClass == null || InheritConstructorContributor.hasInheritConstructorsAnnotation(typeDefinition)) return;

    final PsiMethod[] constructors = typeDefinition.getCodeConstructors();
    checkDefaultConstructors(holder, typeDefinition, superClass, constructors);
    checkRecursiveConstructors(holder, constructors);
  }

  @Override
  public void visitCallExpression(@NotNull GrCallExpression callExpression) {
    if (callExpression.resolveMethod() == null &&
        callExpression.getFirstChild() instanceof GrLiteral &&
        callExpression.getExpressionArguments().length > 0 &&
        callExpression.getExpressionArguments()[0] instanceof GrLiteral) {
      String message = GroovyBundle.message("inspection.message.cannot.resolve.method.call");
      myHolder.newAnnotation(HighlightSeverity.WEAK_WARNING, message).range(callExpression).create();
    }
  }

  private static void checkDefaultConstructors(@NotNull AnnotationHolder holder,
                                               @NotNull GrTypeDefinition typeDefinition,
                                               @NotNull PsiClass superClass,
                                               PsiMethod @NotNull[] constructors) {
    PsiMethod defConstructor = getDefaultConstructor(superClass);
    boolean needExplicitSuperCall = superClass.getConstructors().length != 0 && 
                                    (defConstructor == null || !PsiUtil.isAccessible(typeDefinition, defConstructor));
    if (!needExplicitSuperCall) return;
    final String qName = superClass.getQualifiedName();

    if (!(superClass instanceof GrRecordDefinition) && typeDefinition.getConstructors().length == 0) {
      final TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
      String message = GroovyBundle.message("there.is.no.default.constructor.available.in.class.0", qName);
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(range)
        .withFix(QuickFixFactory.getInstance().createCreateConstructorMatchingSuperFix(typeDefinition)).create();
    }

    for (PsiMethod method : constructors) {
      if (method instanceof GrMethod grMethod) {
        final GrOpenBlock block = grMethod.getBlock();
        if (block == null) continue;
        final GrStatement[] statements = block.getStatements();
        if (statements.length > 0) {
          if (statements[0] instanceof GrConstructorInvocation) continue;
        }
        String message = GroovyBundle.message("there.is.no.default.constructor.available.in.class.0", qName);
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(GrHighlightUtil.getMethodHeaderTextRange(method)).create();
      }
    }

    List<PsiAnnotation> annotations =
      ContainerUtil.filter(typeDefinition.getAnnotations(),
                           anno -> GrGeneratedConstructorUtils.getConstructorGeneratingAnnotations().contains(anno.getQualifiedName()));
    for (PsiAnnotation anno : annotations) {
      PsiNameValuePair preAttribute = AnnotationUtil.findDeclaredAttribute(anno, TupleConstructorAttributes.PRE);
      TextRange errorRange;
      if (preAttribute == null) {
        errorRange = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
      }
      else if (!GeneratedConstructorAnnotationChecker.isSuperCalledInPre(anno)) {
        errorRange = preAttribute.getTextRange();
      }
      else {
        errorRange = null;
      }
      if (errorRange != null) {
        String message = GroovyBundle.message("there.is.no.default.constructor.available.in.class.0", qName);
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(errorRange).create();
      }
    }
  }

  @Override
  public void visitEnumConstant(@NotNull GrEnumConstant enumConstant) {
    super.visitEnumConstant(enumConstant);
    if (GroovyConfigUtils.getInstance().isVersionAtLeast(enumConstant, GroovyConfigUtils.GROOVY2_2)) {
      // Map constructors implemented in Groovy 2.2, see https://issues.apache.org/jira/browse/GROOVY-4485
      return;
    }
    final GrArgumentList argumentList = enumConstant.getArgumentList();

    if (PsiImplUtil.hasNamedArguments(argumentList) && !PsiImplUtil.hasExpressionArguments(argumentList)) {
      final PsiMethod constructor = enumConstant.resolveConstructor();
      if (constructor != null && !PsiUtil.isConstructorHasRequiredParameters(constructor)) {
        final String message = GroovyBundle.message("the.usage.of.a.map.entry.expression.to.initialize.an.enum.is.currently.not.supported");
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(argumentList).create();
      }
    }
  }

  private static void checkRecursiveConstructors(AnnotationHolder holder, PsiMethod @NotNull[] constructors) {
    Map<PsiMethod, PsiMethod> nodes = new HashMap<>(constructors.length);

    Set<PsiMethod> set = Set.of(constructors);

    for (PsiMethod constructor : constructors) {
      if (!(constructor instanceof GrMethod grMethod)) continue;

      final GrOpenBlock block = grMethod.getBlock();
      if (block == null) continue;

      final GrStatement[] statements = block.getStatements();
      if (statements.length == 0 || !(statements[0] instanceof GrConstructorInvocation)) continue;

      final PsiMethod resolved = ((GrConstructorInvocation)statements[0]).resolveMethod();
      if (resolved == null || !set.contains(resolved)) continue;

      nodes.put(constructor, resolved);
    }

    Set<PsiMethod> checked = new HashSet<>();

    for (PsiMethod constructor : constructors) {
      if (!checked.add(constructor)) continue;

      Set<PsiMethod> current = new HashSet<>();
      current.add(constructor);
      for (constructor = nodes.get(constructor); constructor != null && current.add(constructor); constructor = nodes.get(constructor)) {
        checked.add(constructor);
      }

      if (constructor != null) {
        PsiMethod circleStart = constructor;
        do {
          String message = GroovyBundle.message("recursive.constructor.invocation");
          holder.newAnnotation(HighlightSeverity.ERROR, message).range(GrHighlightUtil.getMethodHeaderTextRange(constructor)).create();
          constructor = nodes.get(constructor);
        }
        while (constructor != circleStart);
      }
    }
  }

  @Override
  public void visitUnaryExpression(@NotNull GrUnaryExpression expression) {
    if ((expression.getOperationTokenType() == GroovyTokenTypes.mINC || expression.getOperationTokenType() == GroovyTokenTypes.mDEC)
        && expression.getOperand() instanceof GrReferenceExpression ref
        && ref.getQualifier() == null) {
      GrTraitTypeDefinition trait = PsiTreeUtil.getParentOfType(ref, GrTraitTypeDefinition.class);
      if (trait != null && ref.resolve() instanceof GrField field && field.getContainingClass() instanceof GrTraitTypeDefinition) {
        String message = GroovyBundle.message("0.expressions.on.trait.fields.properties.are.not.supported.in.traits",
                                              expression.getOperationToken().getText());
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).create();
      }
    }
  }

  @Override
  public void visitOpenBlock(@NotNull GrOpenBlock block) {
    PsiElement blockParent = block.getParent();
    if (blockParent instanceof GrMethod method && GrTraitUtil.isMethodAbstract(method)) {
      String message = GrTraitUtil.isInterface(method.getContainingClass())
                       ? GroovyBundle.message("interface.abstract.methods.must.not.have.body")
                       : GroovyBundle.message("abstract.methods.must.not.have.body");
      AnnotationBuilder builder = myHolder.newAnnotation(HighlightSeverity.ERROR, message);
      registerMakeAbstractMethodNotAbstractFix(builder, method, true, message, block.getTextRange()).create();
    }
  }

  @Override
  public void visitField(@NotNull GrField field) {
    super.visitField(field);
    if (field.getTypeElementGroovy() == null && field.getContainingClass() instanceof GrAnnotationTypeDefinition) {
      String message = GroovyBundle.message("annotation.field.should.have.type.declaration");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(field.getNameIdentifierGroovy()).create();
    }
    checkInitializer(field);
  }

  private void checkInitializer(@NotNull GrField field) {
    PsiExpression initializer = field.getInitializer();
    if (initializer == null) return;
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return;
    PsiAnnotation tupleConstructor = containingClass.getAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR);
    if (tupleConstructor == null) return;
    if (!Boolean.FALSE.equals(GrAnnotationUtil.inferBooleanAttribute(tupleConstructor, TupleConstructorAttributes.DEFAULTS))) return;
    AffectedMembersCache cache = GrGeneratedConstructorUtils.getAffectedMembersCache(tupleConstructor);
    if (!cache.arePropertiesHandledByUser() && cache.getAffectedMembers().contains(field)) {
      String message = GroovyBundle.message("initializers.are.forbidden.with.defaults");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(initializer).create();
    }
  }

  @Override
  public void visitMethod(@NotNull GrMethod method) {
    checkDuplicateMethod(method);
    checkMethodWithTypeParamsShouldHaveReturnType(myHolder, method);
    checkInnerMethod(myHolder, method);
    checkOptionalParametersInAbstractMethod(myHolder, method);
    checkConstructorOfImmutableClass(myHolder, method);
    checkGetterOfImmutable(myHolder, method);

    final PsiElement nameIdentifier = method.getNameIdentifierGroovy();
    if (GroovyTokenSets.STRING_LITERALS.contains(nameIdentifier.getNode().getElementType())) {
      checkStringLiteral(nameIdentifier);
    }

    GrOpenBlock block = method.getBlock();
    if (block != null && TypeInferenceHelper.isTooComplexTooAnalyze(block)) {
      String message = GroovyBundle.message("method.0.is.too.complex.too.analyze", method.getName());
      myHolder.newAnnotation(HighlightSeverity.WEAK_WARNING, message).range(nameIdentifier).create();
    }

    final PsiClass containingClass = method.getContainingClass();
    if (method.isConstructor()) {
      if (containingClass instanceof GrAnonymousClassDefinition) {
        String message = GroovyBundle.message("constructors.are.not.allowed.in.anonymous.class");
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(nameIdentifier).create();
      }
      else if (containingClass != null && containingClass.isInterface()) {
        String message = GroovyBundle.message("constructors.are.not.allowed.in.interface");
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(nameIdentifier).create();
      }
    }

    if (method.getBlock() == null && !method.hasModifierProperty(PsiModifier.NATIVE) && !GrTraitUtil.isMethodAbstract(method)) {
      String message = GroovyBundle.message("not.abstract.method.should.have.body");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(nameIdentifier).create();
    }

    checkOverridingMethod(myHolder, method);
  }

  private static void checkGetterOfImmutable(AnnotationHolder holder, GrMethod method) {
    if (!GroovyPropertyUtils.isSimplePropertyGetter(method)) return;

    PsiClass aClass = method.getContainingClass();
    if (aClass == null || !GrImmutableUtils.hasImmutableAnnotation(aClass)) return;

    PsiField field = GroovyPropertyUtils.findFieldForAccessor(method, false);
    if (!(field instanceof GrField grField)) return;

    GrModifierList fieldModifierList = grField.getModifierList();
    if (fieldModifierList == null || fieldModifierList.hasExplicitVisibilityModifiers()) return;

    String message = GroovyBundle.message("repetitive.method.name.0", method.getName());
    holder.newAnnotation(HighlightSeverity.ERROR, message).range(method.getNameIdentifierGroovy()).create();
  }

  private static void checkConstructorOfImmutableClass(AnnotationHolder holder, GrMethod method) {
    if (!method.isConstructor()) return;

    PsiClass aClass = method.getContainingClass();
    if (aClass == null || !GrImmutableUtils.hasImmutableAnnotation(aClass)) return;

    String message = GroovyBundle.message("explicit.constructors.are.not.allowed.in.immutable.class");
    holder.newAnnotation(HighlightSeverity.ERROR, message).range(method.getNameIdentifierGroovy()).create();
  }

  private static void checkOverridingMethod(@NotNull AnnotationHolder holder, @NotNull GrMethod method) {
    final List<HierarchicalMethodSignature> signatures = method.getHierarchicalMethodSignature().getSuperSignatures();

    for (HierarchicalMethodSignature signature : signatures) {
      final PsiMethod superMethod = signature.getMethod();
      if (!GrTraitUtil.isTrait(superMethod.getContainingClass()) && superMethod.hasModifierProperty(PsiModifier.FINAL)) {

        final String current = GroovyPresentationUtil.getSignaturePresentation(method.getSignature(PsiSubstitutor.EMPTY));
        final String superPresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
        final String superQName = getQNameOfMember(superMethod);

        String message =
          GroovyBundle.message("method.0.cannot.override.method.1.in.2.overridden.method.is.final", current, superPresentation, superQName);
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(GrHighlightUtil.getMethodHeaderTextRange(method)).create();

        return;
      }

      final String currentModifier = VisibilityUtil.getVisibilityModifier(method.getModifierList());
      final String superModifier = VisibilityUtil.getVisibilityModifier(superMethod.getModifierList());

      if (PsiModifier.PUBLIC.equals(superModifier) && (PsiModifier.PROTECTED.equals(currentModifier) || PsiModifier.PRIVATE
        .equals(currentModifier)) ||
          PsiModifier.PROTECTED.equals(superModifier) && PsiModifier.PRIVATE.equals(currentModifier)) {
        final String currentPresentation = GroovyPresentationUtil.getSignaturePresentation(method.getSignature(PsiSubstitutor.EMPTY));
        final String superPresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
        final String superQName = getQNameOfMember(superMethod);

        //noinspection MagicConstant
        final PsiElement modifier = method.getModifierList().getModifier(currentModifier);
        String message =
          GroovyBundle.message("method.0.cannot.have.weaker.access.privileges.1.than.2.in.3.4", currentPresentation, currentModifier,
                               superPresentation, superQName, superModifier);
        holder.newAnnotation(HighlightSeverity.ERROR, message)
          .range(modifier != null ? modifier : method.getNameIdentifierGroovy()).create();
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
        String message = GroovyBundle.message("method.with.type.parameters.should.have.return.type");
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(range).create();
      }
    }
  }

  private static void checkOptionalParametersInAbstractMethod(AnnotationHolder holder, GrMethod method) {
    if (!method.hasModifierProperty(PsiModifier.ABSTRACT) || !(method.getContainingClass() instanceof GrInterfaceDefinition)) return;

    for (GrParameter parameter : method.getParameters()) {
      GrExpression initializerGroovy = parameter.getInitializerGroovy();
      if (initializerGroovy != null) {
        PsiElement assignOperator = parameter.getNameIdentifierGroovy();
        TextRange textRange = new TextRange(assignOperator.getTextRange().getEndOffset(), initializerGroovy.getTextRange().getEndOffset());
        String message = GroovyBundle.message("default.initializers.are.not.allowed.in.abstract.method");
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(textRange).create();
      }
    }
  }

  private static @Nullable PsiMethod getDefaultConstructor(PsiClass clazz) {
    final String className = clazz.getName();
    if (className == null) return null;
    final PsiMethod[] byName = clazz.findMethodsByName(className, true);
    if (byName.length == 0) return null;
    Outer:
    for (PsiMethod method : byName) {
      if (method.getParameterList().isEmpty()) return method;
      if (!(method instanceof GrMethod grMethod)) continue;
      final GrParameter[] parameters = grMethod.getParameterList().getParameters();

      for (GrParameter parameter : parameters) {
        if (!parameter.isOptional()) continue Outer;
      }
      return method;
    }
    return null;
  }


  @Override
  public void visitVariable(@NotNull GrVariable variable) {
    if (variable.isUnnamed()) return;
    checkName(variable);

    PsiElement parent = variable.getParent();
    if (parent instanceof GrForInClause clause) {
      PsiElement delimiter = clause.getDelimiter();
      if (delimiter == null) return;
      if (delimiter.getNode().getElementType() == GroovyTokenTypes.mCOLON) {
        GrTypeElement typeElement = variable.getTypeElementGroovy();
        GrModifierList modifierList = variable.getModifierList();
        if (modifierList != null && typeElement == null && StringUtil.isEmptyOrSpaces(modifierList.getText())) {
          String message = GroovyBundle.message("java.style.for.each.statement.requires.a.type.declaration");
          myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(variable.getNameIdentifierGroovy())
            .withFix(new ReplaceDelimiterFix()).create();
        }
      }
    }


    PsiNamedElement duplicate = ResolveUtil.findDuplicate(variable);
    if (duplicate instanceof GrVariable &&
        (variable instanceof GrField || ResolveUtil.isScriptField(variable) || !(duplicate instanceof GrField))) {
      final String key = duplicate instanceof GrField ? "field.already.defined" : "variable.already.defined";
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message(key, variable.getName()))
        .range(variable.getNameIdentifierGroovy()).create();
    }

    if (variable.getDeclaredType() instanceof PsiEllipsisType && !isLastParameter(variable)) {
      TextRange range = getEllipsisRange(variable);
      if (range == null) {
        range = getTypeRange(variable);
      }
      if (range != null) {
        myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("ellipsis.type.is.not.allowed.here")).range(range).create();
      }
    }
  }

  private static @Nullable TextRange getEllipsisRange(GrVariable variable) {
    if (variable instanceof GrParameter parameter) {
      final PsiElement dots = parameter.getEllipsisDots();
      if (dots != null) {
        return dots.getTextRange();
      }
    }
    return null;
  }

  private static @Nullable TextRange getTypeRange(GrVariable variable) {
    GrTypeElement typeElement = variable.getTypeElementGroovy();
    if (typeElement == null) return null;

    PsiElement sibling = typeElement.getNextSibling();
    if (sibling != null && sibling.getNode().getElementType() == GroovyTokenTypes.mTRIPLE_DOT) {
      return new TextRange(typeElement.getTextRange().getStartOffset(), sibling.getTextRange().getEndOffset());
    }

    return typeElement.getTextRange();
  }


  private static boolean isLastParameter(PsiVariable variable) {
    if (!(variable instanceof PsiParameter) || !(variable.getParent() instanceof PsiParameterList list)) return false;

    PsiParameter[] parameters = list.getParameters();
    return parameters.length > 0 && parameters[parameters.length - 1] == variable;
  }

  private void checkName(GrVariable variable) {
    if (!"$".equals(variable.getName())) return;
    String message = GroovyBundle.message("incorrect.variable.name");
    myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(variable.getNameIdentifierGroovy()).create();
  }

  @Override
  public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
    GrExpression lValue = expression.getLValue();
    if (!PsiUtil.mightBeLValue(lValue)) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("invalid.lvalue")).range(lValue).create();
    }
  }

  @Override
  public void visitReturnStatement(@NotNull GrReturnStatement returnStatement) {
    final GrExpression value = returnStatement.getReturnValue();
    if (value != null) {
      final PsiType type = value.getType();
      if (type != null) {
        final GrParameterListOwner owner = PsiTreeUtil.getParentOfType(returnStatement, GrParameterListOwner.class);
        if (owner instanceof PsiMethod method) {
          if (method.isConstructor()) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("cannot.return.from.constructor")).range(value).create();
          }
          else {
            final PsiType methodType = method.getReturnType();
            if (methodType != null && PsiTypes.voidType().equals(methodType)) {
              String message = GroovyBundle.message("cannot.return.from.void.method");
              myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(value).create();
            }
          }
        }
      }
    }
  }

  @Override
  public void visitTypeParameterList(@NotNull GrTypeParameterList list) {
    final PsiElement parent = list.getParent();
    if (parent instanceof GrMethod method && method.isConstructor() ||
        parent instanceof GrEnumTypeDefinition ||
        parent instanceof GrAnnotationTypeDefinition) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("type.parameters.are.unexpected")).create();
    }
  }

  @Override
  public void visitListOrMap(@NotNull GrListOrMap listOrMap) {
    final GroovyConstructorReference constructorReference = listOrMap.getConstructorReference();
    if (constructorReference != null) {
      myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(listOrMap.getLBrack())
        .textAttributes(GroovySyntaxHighlighter.LITERAL_CONVERSION).create();
      final PsiElement rBracket = listOrMap.getRBrack();
      if (rBracket != null) {
        myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(rBracket)
          .textAttributes(GroovySyntaxHighlighter.LITERAL_CONVERSION).create();
      }
    }

    final GrNamedArgument[] namedArguments = listOrMap.getNamedArguments();
    final GrExpression[] expressionArguments = listOrMap.getInitializers();

    if (namedArguments.length != 0 && expressionArguments.length != 0) {
      String message = GroovyBundle.message("collection.literal.contains.named.argument.and.expression.items");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).create();
    }

    checkNamedArgs(namedArguments, false);
  }

  @Override
  public void visitClassTypeElement(@NotNull GrClassTypeElement typeElement) {
    super.visitClassTypeElement(typeElement);

    final GrTypeArgumentList argList = typeElement.getReferenceElement().getTypeArgumentList();
    if (argList == null) return;

    for (GrTypeElement element : argList.getTypeArgumentElements()) {
      checkTypeArgForPrimitive(element, GroovyBundle.message("primitive.type.parameters.are.not.allowed"));
    }
  }

  @Override
  public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement refElement) {
    if (refElement.getParent() instanceof GrAnnotation && refElement.resolve() instanceof PsiClass aClass && !aClass.isAnnotationType() &&
        GrAnnotationCollector.findAnnotationCollector(aClass) != null) {
      myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(GroovySyntaxHighlighter.ANNOTATION).create();
    }
    checkUnresolvedCodeReference(refElement, myHolder);
    checkInnerClassReferenceFromInstanceContext(refElement, myHolder);
  }

  @Override
  public void visitTypeElement(@NotNull GrTypeElement typeElement) {
    final PsiElement parent = typeElement.getParent();
    if (!(parent instanceof GrMethod method)) return;

    if (parent instanceof GrAnnotationMethod) {
      checkAnnotationAttributeType(typeElement, myHolder);
    }
    else if (method.isConstructor()) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("constructors.cannot.have.return.type")).create();
    }
    else {
      checkMethodReturnType(method, typeElement, myHolder);
    }
  }

  @Override
  public void visitArrayTypeElement(@NotNull GrArrayTypeElement typeElement) {
    GrTypeElement componentTypeElement = typeElement.getComponentTypeElement();
    PsiType componentType = componentTypeElement.getType();
    if (PsiTypes.voidType().equals(componentType)) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("illegal.type.void")).range(componentTypeElement).create();
    }
    else {
      super.visitArrayTypeElement(typeElement);
    }
  }

  @Override
  public void visitModifierList(@NotNull GrModifierList modifierList) {
    final PsiElement parent = modifierList.getParent();
    if (parent instanceof GrMethod method) {
      checkMethodDefinitionModifiers(myHolder, method);
    }
    else if (parent instanceof GrVariableDeclaration declaration) {
      if (isFieldDeclaration(declaration)) {
        checkFieldModifiers(myHolder, declaration);
      }
      else {
        checkVariableModifiers(myHolder, declaration);
        GrVariable[] variables = declaration.getVariables();
        checkDuplicateModifiers(myHolder, modifierList, variables.length == 0 ? null : variables[0]);
      }
    }
    else if (parent instanceof GrClassInitializer) {
      checkClassInitializerModifiers(myHolder, modifierList);
    }
    else if (parent instanceof GrParameter) {
      checkDuplicateModifiers(myHolder, modifierList, parent);
    }
  }

  private static void checkClassInitializerModifiers(AnnotationHolder holder, GrModifierList modifierList) {
    for (GrAnnotation annotation : modifierList.getAnnotations()) {
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("initializer.cannot.have.annotations")).range(annotation).create();
    }

    for (@GrModifier.GrModifierConstant String modifier : GrModifier.GROOVY_MODIFIERS) {
      if (PsiModifier.STATIC.equals(modifier)) continue;
      checkModifierIsNotAllowed(modifierList, modifier, GroovyBundle.message("initializer.cannot.be.0", modifier), holder);
    }
  }

  @Override
  public void visitClassInitializer(@NotNull GrClassInitializer initializer) {
    if (GrTraitUtil.isInterface(initializer.getContainingClass())) {
      final TextRange range = GrHighlightUtil.getInitializerHeaderTextRange(initializer);
      String message = GroovyBundle.message("initializers.are.not.allowed.in.interface");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(range).create();
    }
  }

  private static void checkFieldModifiers(AnnotationHolder holder, GrVariableDeclaration fieldDeclaration) {
    GrVariable[] variables = fieldDeclaration.getVariables();
    if (variables.length == 0) return;

    GrVariable variable = variables[0];
    final GrField member = variable instanceof GrField field ? field : findScriptField(variable);
    if (member == null) return;

    final GrModifierList modifierList = fieldDeclaration.getModifierList();

    checkAccessModifiers(holder, modifierList, member);
    checkDuplicateModifiers(holder, modifierList, member);

    if (modifierList.hasExplicitModifier(PsiModifier.VOLATILE) && modifierList.hasExplicitModifier(PsiModifier.FINAL)) {
      String message = GroovyBundle.message("illegal.combination.of.modifiers.volatile.and.final");
      AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(modifierList);
      builder = registerLocalFix(builder, new GrModifierFix(member, PsiModifier.VOLATILE, true, false, GrModifierFix.MODIFIER_LIST),
                                 modifierList, message, ProblemHighlightType.ERROR, modifierList.getTextRange());
      builder = registerLocalFix(builder, new GrModifierFix(member, PsiModifier.FINAL, true, false, GrModifierFix.MODIFIER_LIST),
                                 modifierList, message, ProblemHighlightType.ERROR, modifierList.getTextRange());
      builder.create();
    }

    if (member.getContainingClass() instanceof GrInterfaceDefinition) {
      String message1 = GroovyBundle.message("interface.members.are.not.allowed.to.be", PsiModifier.PRIVATE);
      checkModifierIsNotAllowed(modifierList, PsiModifier.PRIVATE, message1, holder);
      String message2 = GroovyBundle.message("interface.members.are.not.allowed.to.be", PsiModifier.PROTECTED);
      checkModifierIsNotAllowed(modifierList, PsiModifier.PROTECTED, message2, holder);
    }
  }

  private static void checkAnnotationAttributeType(GrTypeElement element, AnnotationHolder holder) {
    if (element instanceof GrBuiltInTypeElement) return;

    if (element instanceof GrArrayTypeElement array) {
      checkAnnotationAttributeType(array.getComponentTypeElement(), holder);
      return;
    }
    else if (element instanceof GrClassTypeElement typeElement) {
      final PsiElement resolved = typeElement.getReferenceElement().resolve();
      if (resolved instanceof PsiClass aClass) {
        if (CommonClassNames.JAVA_LANG_STRING.equals(aClass.getQualifiedName())) return;
        if (CommonClassNames.JAVA_LANG_CLASS.equals(aClass.getQualifiedName())) return;
        if (aClass.isAnnotationType()) return;
        if (aClass.isEnum()) return;
      }
    }

    String message = GroovyBundle.message("unexpected.attribute.type.0", element.getType());
    holder.newAnnotation(HighlightSeverity.ERROR, message).range(element).create();
  }

  static void checkMethodReturnType(PsiMethod method, PsiElement toHighlight, AnnotationHolder holder) {
    final HierarchicalMethodSignature signature = method.getHierarchicalMethodSignature();
    final List<HierarchicalMethodSignature> superSignatures = signature.getSuperSignatures();

    PsiType returnType = signature.getSubstitutor().substitute(method.getReturnType());

    for (HierarchicalMethodSignature superMethodSignature : superSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      PsiType declaredReturnType = superMethod.getReturnType();
      PsiType superReturnType = superMethodSignature.getSubstitutor().substitute(declaredReturnType);
      if (PsiTypes.voidType().equals(superReturnType) && method instanceof GrMethod grMethod
          && grMethod.getReturnTypeElementGroovy() == null) return;
      if (superMethodSignature.isRaw()) superReturnType = TypeConversionUtil.erasure(declaredReturnType);
      if (returnType == null || superReturnType == null || method == superMethod) continue;
      PsiClass superClass = superMethod.getContainingClass();
      if (superClass == null) continue;
      String highlightInfo = checkSuperMethodSignature(superMethod, superMethodSignature, superReturnType, method, signature, returnType);
      if (highlightInfo != null) {
        holder.newAnnotation(HighlightSeverity.ERROR, highlightInfo).range(toHighlight).create();
        return;
      }
    }
  }

  private static @Nullable @InspectionMessage String checkSuperMethodSignature(@NotNull PsiMethod superMethod,
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

  private static @NotNull PsiType substituteSuperReturnType(@NotNull MethodSignatureBackedByPsiMethod superMethodSignature,
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

  private static @NotNull @NlsSafe String getQNameOfMember(@NotNull PsiMember member) {
    final PsiClass aClass = member.getContainingClass();
    return getQName(aClass);
  }

  private static @NotNull @NlsSafe String getQName(@Nullable PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass anonymous) {
      return GroovyBundle.message("anonymous.class.derived.from.0", anonymous.getBaseClassType().getCanonicalText());
    }
    if (aClass != null) {
      final String qname = aClass.getQualifiedName();
      if (qname != null) {
        return qname;
      }
    }
    return "<null>";
  }


  private void checkTypeArgForPrimitive(@Nullable GrTypeElement element, @NotNull @InspectionMessage String message) {
    if (element == null || !(element.getType() instanceof PsiPrimitiveType)) return;

    AnnotationBuilder builder = myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(element);
    builder = registerLocalFix(builder, new GrReplacePrimitiveTypeWithWrapperFix(element), element, message, ProblemHighlightType.ERROR,
                               element.getTextRange());
    builder.create();
  }

  @Override
  public void visitWildcardTypeArgument(@NotNull GrWildcardTypeArgument wildcardTypeArgument) {
    super.visitWildcardTypeArgument(wildcardTypeArgument);

    checkTypeArgForPrimitive(wildcardTypeArgument.getBoundTypeElement(), GroovyBundle.message("primitive.bound.types.are.not.allowed"));
  }

  private void highlightNamedArgs(GrNamedArgument[] namedArguments) {
    for (GrNamedArgument namedArgument : namedArguments) {
      final GrArgumentLabel label = namedArgument.getLabel();
      if (label != null && label.getExpression() == null && label.getNameElement().getNode().getElementType() != GroovyTokenTypes.mSTAR) {
        myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(label).textAttributes(GroovySyntaxHighlighter.MAP_KEY).create();
      }
    }
  }

  private void checkNamedArgs(GrNamedArgument[] namedArguments, boolean forArgList) {
    highlightNamedArgs(namedArguments);

    Set<Object> existingKeys = new HashSet<>();
    for (GrNamedArgument namedArgument : namedArguments) {
      GrArgumentLabel label = namedArgument.getLabel();
      Object value = PsiUtil.getLabelValue(label);
      if (value == null) continue;
      if (value == ObjectUtils.NULL) value = null;
      if (existingKeys.add(value)) continue;
      if (forArgList) {
        String message = GroovyBundle.message("duplicated.named.parameter", String.valueOf(value));
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(label).create();
      }
      else {
        String message = GroovyBundle.message("duplicate.element.in.the.map", String.valueOf(value));
        myHolder.newAnnotation(HighlightSeverity.WARNING, message).range(label).create();
      }
    }
  }

  @Override
  public void visitNewExpression(@NotNull GrNewExpression newExpression) {
    GrTypeArgumentList constructorTypeArguments = newExpression.getConstructorTypeArguments();
    if (constructorTypeArguments != null) {
      String message = GroovyBundle.message("groovy.does.not.support.constructor.type.arguments");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(constructorTypeArguments).create();
    }

    final GrTypeElement typeElement = newExpression.getTypeElement();
    if (typeElement instanceof GrBuiltInTypeElement) {
      if (newExpression.getArrayCount() == 0) {
        String message = GroovyBundle.message("create.instance.of.built-in.type");
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(typeElement).create();
      }
    }

    if (newExpression.getArrayCount() > 0) return;

    GrCodeReferenceElement refElement = newExpression.getReferenceElement();
    if (refElement == null) return;

    if (refElement.resolve() instanceof PsiClass clazz
        && clazz.hasModifierProperty(PsiModifier.ABSTRACT)
        && newExpression.getAnonymousClassDefinition() == null) {
      String message = clazz.isInterface()
                       ? GroovyBundle.message("cannot.instantiate.interface", clazz.getName())
                       : GroovyBundle.message("cannot.instantiate.abstract.class", clazz.getName());
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(refElement).create();
    }
  }

  @Override
  public void visitArgumentList(@NotNull GrArgumentList list) {
    checkNamedArgs(list.getNamedArguments(), true);
  }

  @Override
  public void visitBreakStatement(@NotNull GrBreakStatement breakStatement) {
    checkFlowInterruptStatement(breakStatement, myHolder);
  }

  @Override
  public void visitContinueStatement(@NotNull GrContinueStatement continueStatement) {
    checkFlowInterruptStatement(continueStatement, myHolder);
  }

  @Override
  public void visitPackageDefinition(@NotNull GrPackageDefinition packageDefinition) {
    checkAnnotationList(myHolder, packageDefinition.getAnnotationList(), GroovyBundle.message("package.definition.cannot.have.modifiers"));
  }

  @Override
  public void visitLambdaExpression(@NotNull GrLambdaExpression expression) {
    super.visitLambdaExpression(expression);

    myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(expression.getArrow())
      .textAttributes(GroovySyntaxHighlighter.LAMBDA_ARROW_AND_BRACES).create();
  }

  @Override
  public void visitBlockLambdaBody(@NotNull GrBlockLambdaBody body) {
    super.visitBlockLambdaBody(body);

    PsiElement lBrace = body.getLBrace();
    if (lBrace != null) {
      myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(lBrace)
        .textAttributes(GroovySyntaxHighlighter.LAMBDA_ARROW_AND_BRACES).create();
    }

    PsiElement rBrace = body.getRBrace();
    if (rBrace != null) {
      myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(rBrace)
        .textAttributes(GroovySyntaxHighlighter.LAMBDA_ARROW_AND_BRACES).create();
    }
  }

  @Override
  public void visitClosure(@NotNull GrClosableBlock closure) {
    super.visitClosure(closure);

    myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(closure.getLBrace())
      .textAttributes(GroovySyntaxHighlighter.CLOSURE_ARROW_AND_BRACES).create();
    PsiElement rBrace = closure.getRBrace();
    if (rBrace != null) {
      myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(rBrace)
        .textAttributes(GroovySyntaxHighlighter.CLOSURE_ARROW_AND_BRACES).create();
    }
    PsiElement closureArrow = closure.getArrow();
    if (closureArrow != null) {
      myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(closureArrow)
        .textAttributes(GroovySyntaxHighlighter.CLOSURE_ARROW_AND_BRACES).create();
    }

    if (!FunctionalExpressionFlowUtil.isFlatDFAAllowed()) {
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
        String message = GroovyBundle.message("closure.is.too.complex.to.analyze");
        myHolder.newAnnotation(HighlightSeverity.WEAK_WARNING, message).range(new TextRange(startOffset, endOffset)).create();
      }
    }
  }

  @Override
  public void visitLiteralExpression(@NotNull GrLiteral literal) {
    final IElementType elementType = literal.getFirstChild().getNode().getElementType();
    if (GroovyTokenSets.STRING_LITERALS.contains(elementType)) {
      checkStringLiteral(literal);
    }
    else if (elementType == GroovyTokenTypes.mREGEX_LITERAL || elementType == GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL) {
      checkRegexLiteral(literal.getFirstChild());
    }
  }

  @Override
  public void visitRegexExpression(@NotNull GrRegex regex) {
    checkRegexLiteral(regex);
  }

  private void checkRegexLiteral(PsiElement regex) {
    String[] parts =
      regex instanceof GrRegex grRegex ? grRegex.getTextParts() : new String[]{regex.getFirstChild().getNextSibling().getText()};

    for (String part : parts) {
      if (!GrStringUtil.parseRegexCharacters(part, new StringBuilder(part.length()), null, regex.getText().startsWith("/"))) {
        String message = GroovyBundle.message("illegal.escape.character.in.string.literal");
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(regex).create();
        return;
      }
    }
  }

  @Override
  public void visitGStringExpression(@NotNull GrString gstring) {
    for (GrStringContent part : gstring.getContents()) {
      final String text = part.getText();
      if (!GrStringUtil.parseStringCharacters(text, new StringBuilder(text.length()), null)) {
        String message = GroovyBundle.message("illegal.escape.character.in.string.literal");
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(part).create();
        return;
      }
    }
  }

  @Override
  public void visitGStringInjection(@NotNull GrStringInjection injection) {
    if (((GrString)injection.getParent()).isPlainString()) {
      PsiElement lineFeed = getLineFeed(injection);
      if (lineFeed != null) {
        String message = GroovyBundle.message("injection.should.not.contain.line.feeds");
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(lineFeed).create();
      }
    }
  }

  private void checkStringLiteral(PsiElement literal) {
    InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(literal.getProject());
    String text = injectedLanguageManager.isInjectedFragment(literal.getContainingFile())
                  ? injectedLanguageManager.getUnescapedText(literal)
                  : literal.getText();
    assert text != null;

    String quote = GrStringUtil.getStartQuote(text);
    if (quote.isEmpty()) return;

    String substring = text.substring(quote.length());
    if (!GrStringUtil.parseStringCharacters(substring, new StringBuilder(text.length()), null)) {
      String message = GroovyBundle.message("illegal.escape.character.in.string.literal");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(literal).create();
      return;
    }

    int[] offsets = new int[substring.length() + 1];
    StringBuilder builder = new StringBuilder(text.length());
    boolean result = GrStringUtil.parseStringCharacters(substring, builder, offsets);
    LOG.assertTrue(result);
    if (!builder.toString().endsWith(quote) || substring.charAt(offsets[builder.length() - quote.length()]) == '\\') {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("string.end.expected")).create();
    }
  }

  @Override
  public void visitForInClause(@NotNull GrForInClause forInClause) {
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
      if (GrModifier.VAR.equals(modifierText)) continue;
      if (GrModifier.VAL.equals(modifierText)) continue;
      String message = GroovyBundle.message("not.allowed.modifier.in.for.in", modifierText);
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(modifier).create();
    }
  }

  @Override
  public void visitFile(@NotNull GroovyFileBase file) {
    if (file.getScriptClass() != null) {
      checkSameNameMethodsWithDifferentAccessModifiers(myHolder, file.getMethods());
    }
  }

  @Override
  public void visitAnnotation(@NotNull GrAnnotation annotation) {
    AnnotationChecker.checkApplicability(annotation, annotation.getOwner(), myHolder, annotation.getClassReference());
  }

  @Override
  public void visitAnnotationArgumentList(@NotNull GrAnnotationArgumentList annotationArgumentList) {
    GrAnnotation annotation = (GrAnnotation)annotationArgumentList.getParent();
    Pair<PsiElement, @InspectionMessage String> r = AnnotationChecker.checkAnnotationArgumentList(annotation, myHolder);
    if (r != null && r.getFirst() != null && r.getSecond() != null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, r.getSecond()).range(r.getFirst()).create();
    }
  }

  @Override
  public void visitAnnotationMethod(@NotNull GrAnnotationMethod annotationMethod) {
    super.visitAnnotationMethod(annotationMethod);

    final PsiReferenceList list = annotationMethod.getThrowsList();
    if (list.getReferencedTypes().length > 0) {
      String message = GroovyBundle.message("throws.clause.is.not.allowed.in.at.interface");
      myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(list).create();
    }

    final GrAnnotationMemberValue value = annotationMethod.getDefaultValue();
    if (value == null) return;

    Pair.NonNull<PsiElement, @InspectionMessage String> result =
      CustomAnnotationChecker.checkAnnotationValueByType(value, annotationMethod.getReturnType(), false);
    if (result != null) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, result.getSecond()).range(result.getFirst()).create();
    }
  }

  @Override
  public void visitAnnotationNameValuePair(@NotNull GrAnnotationNameValuePair nameValuePair) {
    final PsiElement identifier = nameValuePair.getNameIdentifierGroovy();
    if (identifier == null && nameValuePair.getParent() instanceof GrAnnotationArgumentList list) {
      final int count = list.getAttributes().length;
      if (count > 1) {
        myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("attribute.name.expected")).create();
      }
    }

    final GrAnnotationMemberValue value = nameValuePair.getValue();
    if (value != null) {
      checkAnnotationAttributeValue(value, value);
    }
  }

  private boolean checkAnnotationAttributeValue(@Nullable GrAnnotationMemberValue value, @NotNull PsiElement toHighlight) {
    if (value == null) return false;
    if (value instanceof GrLiteral || value instanceof GrFunctionalExpression || value instanceof GrAnnotation) return false;

    if (value instanceof GrReferenceExpression ref) {
      PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass) return false;
      if (resolved instanceof PsiEnumConstant) return false;
      if (resolved == null && isClassReference(value)) return false;

      if (resolved instanceof GrAccessorMethod accessor) resolved = accessor.getProperty();
      if (resolved instanceof PsiField psiField) {
        GrExpression initializer;
        try {
          if (resolved instanceof GrField field) {
            initializer = field.getInitializerGroovy();
          }
          else {
            final PsiExpression _initializer = psiField.getInitializer();
            initializer = _initializer != null
                          ? (GrExpression)ExpressionConverter.getExpression(_initializer, GroovyLanguage.INSTANCE, value.getProject())
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
    if (value instanceof GrAnnotationArrayInitializer initializer) {
      for (GrAnnotationMemberValue expression : initializer.getInitializers()) {
        if (checkAnnotationAttributeValue(expression, toHighlight)) return true;
      }
      return false;
    }
    if (value instanceof GrUnaryExpression unary) {
      final IElementType tokenType = unary.getOperationTokenType();
      if (tokenType == GroovyTokenTypes.mMINUS || tokenType == GroovyTokenTypes.mPLUS) {
        return checkAnnotationAttributeValue(unary.getOperand(), toHighlight);
      }
    }

    String message = GroovyBundle.message("expected.0.to.be.inline.constant", value.getText());
    myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(toHighlight).create();
    return true;
  }

  private static boolean isClassReference(GrAnnotationMemberValue value) {
    return value instanceof GrReferenceExpression ref
           && "class".equals(ref.getReferenceName())
           && ref.getQualifier() instanceof GrReferenceExpression q
           && q.resolve() instanceof PsiClass;
  }

  @Override
  public void visitImportStatement(@NotNull GrImportStatement importStatement) {
    checkAnnotationList(myHolder, importStatement.getAnnotationList(), GroovyBundle.message("import.statement.cannot.have.modifiers"));
    if (importStatement.isOnDemand() || importStatement.isModule()) {
      GrImportAlias alias = importStatement.getAlias();
      if (alias != null) {
        String message = GroovyBundle.message("import.alias.not.allowed.here", importStatement.isOnDemand() ? 1 : 2);
        myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(alias)
          .withFix(QuickFixFactory.getInstance().createDeleteFix(alias, GroovyBundle.message("import.alias.remove.fix"))).create();
      }
    }
  }

  @Override
  public void visitExtendsClause(@NotNull GrExtendsClause extendsClause) {
    GrTypeDefinition typeDefinition = (GrTypeDefinition)extendsClause.getParent();

    if (typeDefinition.isAnnotationType()) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("annotation.types.may.not.have.extends.clause")).create();
    }
    else if (typeDefinition.isTrait()) {
      checkReferenceList(myHolder, extendsClause, IS_TRAIT, GroovyBundle.message("only.traits.expected.here"), null);
    }
    else if (typeDefinition.isInterface()) {
      checkReferenceList(myHolder, extendsClause, IS_INTERFACE, GroovyBundle.message("no.class.expected.here"), null);
    }
    else if (typeDefinition.isEnum()) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("enums.may.not.have.extends.clause")).create();
    }
    else {
      checkReferenceList(myHolder, extendsClause, IS_NOT_INTERFACE, GroovyBundle.message("no.interface.expected.here"),
                         new ChangeExtendsImplementsQuickFix(typeDefinition).asIntention());
      checkForWildCards(myHolder, extendsClause);
    }
  }

  @Override
  public void visitImplementsClause(@NotNull GrImplementsClause implementsClause) {
    GrTypeDefinition typeDefinition = (GrTypeDefinition)implementsClause.getParent();

    if (typeDefinition.isAnnotationType()) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("annotation.types.may.not.have.implements.clause")).create();
    }
    else if (GrTraitUtil.isInterface(typeDefinition)) {
      myHolder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("no.implements.clause.allowed.for.interface"))
        .withFix(new ChangeExtendsImplementsQuickFix(typeDefinition)).create();
    }
    else {
      IntentionAction fix =
        typeDefinition instanceof GrRecordDefinition ? null : new ChangeExtendsImplementsQuickFix(typeDefinition).asIntention();
      checkReferenceList(myHolder, implementsClause, IS_INTERFACE, GroovyBundle.message("no.class.expected.here"), fix);
      checkForWildCards(myHolder, implementsClause);
    }
  }

  private static void checkReferenceList(@NotNull AnnotationHolder holder,
                                         @NotNull GrReferenceList list,
                                         @NotNull Condition<? super PsiClass> applicabilityCondition,
                                         @NotNull @InspectionMessage String message,
                                         @Nullable IntentionAction fix) {
    for (GrCodeReferenceElement refElement : list.getReferenceElementsGroovy()) {
      if (refElement.resolve() instanceof PsiClass aClass && !applicabilityCondition.value(aClass)) {
        AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(refElement);
        if (fix != null) {
          builder = builder.withFix(fix);
        }
        builder.create();
      }
    }
  }

  private static void checkFlowInterruptStatement(GrFlowInterruptingStatement statement, AnnotationHolder holder) {
    final PsiElement label = statement.getLabelIdentifier();

    if (label != null && statement.resolveLabel() == null) {
      String message = GroovyBundle.message("undefined.label", statement.getLabelName());
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(label).create();
    }

    final GrStatement targetStatement = statement.findTargetStatement();
    if (targetStatement == null) {
      if (statement instanceof GrContinueStatement && label == null) {
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("continue.outside.loop")).create();
      }
      else if (statement instanceof GrBreakStatement && label == null) {
        holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("break.outside.loop.or.switch")).create();
      }
    }
    if (statement instanceof GrBreakStatement && label != null && findFirstLoop(statement) == null) {
      holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("break.outside.loop")).create();
    }
  }

  private static @Nullable GrLoopStatement findFirstLoop(GrFlowInterruptingStatement statement) {
    return PsiTreeUtil.getParentOfType(statement, GrLoopStatement.class, true, GrClosableBlock.class, GrMember.class, GroovyFile.class);
  }

  private static void checkThisOrSuperReferenceExpression(GrReferenceExpression ref, AnnotationHolder holder) {
    PsiElement nameElement = ref.getReferenceNameElement();
    if (nameElement == null) return;

    IElementType elementType = nameElement.getNode().getElementType();
    if (!(elementType == GroovyTokenTypes.kSUPER || elementType == GroovyTokenTypes.kTHIS)) return;

    final GrExpression qualifier = ref.getQualifier();
    if (qualifier instanceof GrReferenceExpression qRef) {
      final PsiElement resolved = qRef.resolve();
      if (resolved instanceof PsiClass aClass) {
        GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(ref, GrTypeDefinition.class, true, GroovyFile.class);

        if (elementType == GroovyTokenTypes.kSUPER && containingClass != null && GrTraitUtil.isTrait(aClass)) {
          PsiClassType[] superTypes = containingClass.getSuperTypes();
          if (ContainerUtil.find(superTypes, type -> ref.getManager().areElementsEquivalent(type.resolve(), resolved)) != null) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(nameElement)
              .textAttributes(GroovySyntaxHighlighter.KEYWORD).create();
            return; // reference to trait method
          }
        }

        if (containingClass == null || containingClass.getContainingClass() == null && !containingClass.isAnonymous()) {
          String message = GroovyBundle.message("qualified.0.is.allowed.only.in.nested.or.inner.classes", nameElement.getText());
          holder.newAnnotation(HighlightSeverity.ERROR, message).create();
          return;
        }

        if (!PsiTreeUtil.isAncestor(resolved, ref, true)) {
          String qname = aClass.getQualifiedName();
          assert qname != null;
          holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("is.not.enclosing.class", qname)).create();
        }
      }
    }
    else if (qualifier == null) {
      if (elementType == GroovyTokenTypes.kSUPER) {
        final GrMember container = PsiTreeUtil.getParentOfType(ref, GrMethod.class, GrClassInitializer.class);
        if (container != null && container.hasModifierProperty(PsiModifier.STATIC)) {
          holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("super.cannot.be.used.in.static.context")).create();
        }
      }
    }
  }

  private static void checkGrDocReferenceElement(AnnotationHolder holder, PsiElement element) {
    ASTNode node = element.getNode();
    if (node != null && TokenSets.BUILT_IN_TYPES.contains(node.getElementType())) {
      holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(GroovySyntaxHighlighter.KEYWORD).create();
    }
  }

  private static void checkAnnotationList(AnnotationHolder holder,
                                          @NotNull GrModifierList modifierList,
                                          @NotNull @InspectionMessage String message) {
    for (PsiElement modifier : modifierList.getModifiers()) {
      if (!(modifier instanceof PsiAnnotation)) {
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(modifier).create();
      }
    }
  }

  private static void checkImplementedMethodsOfClass(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.hasModifierProperty(PsiModifier.ABSTRACT)) return;
    if (typeDefinition.isAnnotationType()) return;
    if (typeDefinition instanceof GrTypeParameter) return;

    PsiMethod abstractMethod = ClassUtil.getAnyAbstractMethod(typeDefinition);
    if (abstractMethod == null) return;

    final TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
    String message = GroovyBundle.message("method.is.not.implemented", abstractMethod.getName());
   AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(range);
    registerImplementsMethodsFix(typeDefinition, abstractMethod, builder, message, range).create();
  }

  @Contract(pure = true)
  private static AnnotationBuilder registerImplementsMethodsFix(@NotNull GrTypeDefinition typeDefinition,
                                                                @NotNull PsiMethod abstractMethod,
                                                                @NotNull AnnotationBuilder builder,
                                                                @InspectionMessage String message,
                                                                TextRange range) {
    if (!OverrideImplementExploreUtil.getMethodsToOverrideImplement(typeDefinition, true).isEmpty()) {
      builder = builder.withFix(QuickFixFactory.getInstance().createImplementMethodsFix(typeDefinition));
    }

    if (!JavaPsiFacade.getInstance(typeDefinition.getProject()).getResolveHelper().isAccessible(abstractMethod, typeDefinition, null)) {
      GrModifierFix fix1 = new GrModifierFix(abstractMethod, PsiModifier.PUBLIC, true, true, GrModifierFix.MODIFIER_LIST_OWNER);
      builder = registerLocalFix(builder, fix1, abstractMethod, message, ProblemHighlightType.ERROR, range);
      GrModifierFix fix2 = new GrModifierFix(abstractMethod, PsiModifier.PROTECTED, true, true, GrModifierFix.MODIFIER_LIST_OWNER);
      builder = registerLocalFix(builder, fix2, abstractMethod, message, ProblemHighlightType.ERROR, range);
    }

    if (!(typeDefinition instanceof GrAnnotationTypeDefinition) && typeDefinition.getModifierList() != null) {
      GrModifierFix fix = new GrModifierFix(typeDefinition, PsiModifier.ABSTRACT, false, true, GrModifierFix.MODIFIER_LIST_OWNER);
      builder = registerLocalFix(builder, fix, typeDefinition, message, ProblemHighlightType.ERROR, range);
    }
    return builder;
  }

  private static void checkInnerMethod(AnnotationHolder holder, GrMethod grMethod) {
    final PsiElement parent = grMethod.getParent();
    if (parent instanceof GrOpenBlock || parent instanceof GrClosableBlock || parent instanceof GrBlockLambdaBody) {
      String message = GroovyBundle.message("Inner.methods.are.not.supported");
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(grMethod.getNameIdentifierGroovy()).create();
    }
  }

  @Contract(pure = true)
  private static @NotNull AnnotationBuilder registerMakeAbstractMethodNotAbstractFix(AnnotationBuilder builder,
                                                                                     GrMethod method,
                                                                                     boolean makeClassAbstract,
                                                                                     @InspectionMessage String message, TextRange range) {
    builder = method.getBlock() == null
              ? builder.withFix(QuickFixFactory.getInstance().createAddMethodBodyFix(method))
              : builder.withFix(QuickFixFactory.getInstance().createDeleteMethodBodyFix(method));

    GrModifierFix fix = new GrModifierFix(method, PsiModifier.ABSTRACT, false, false, GrModifierFix.MODIFIER_LIST_OWNER);
    builder = registerLocalFix(builder, fix, method, message, ProblemHighlightType.ERROR, range);
    if (makeClassAbstract) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        final PsiModifierList list = containingClass.getModifierList();
        if (list != null && !list.hasModifierProperty(PsiModifier.ABSTRACT)) {
          GrModifierFix fix1 = new GrModifierFix(containingClass, PsiModifier.ABSTRACT, false, true, GrModifierFix.MODIFIER_LIST_OWNER);
          builder = registerLocalFix(builder, fix1, containingClass, message, ProblemHighlightType.ERROR, range);
        }
      }
    }
    return builder;
  }

  private static void checkMethodDefinitionModifiers(AnnotationHolder holder, GrMethod method) {
    final GrModifierList modifiersList = method.getModifierList();
    checkAccessModifiers(holder, modifiersList, method);
    checkDuplicateModifiers(holder, modifiersList, method);
    checkOverrideAnnotation(holder, modifiersList, method);

    checkModifierIsNotAllowed(modifiersList, PsiModifier.VOLATILE,
                              GroovyBundle.message("modifier.0.not.allowed.on.method", PsiModifier.VOLATILE), holder);
    checkModifierIsNotAllowed(modifiersList, GrModifier.VAR,
                              GroovyBundle.message("modifier.0.not.allowed.on.method", GrModifier.VAR), holder);
    checkModifierIsNotAllowed(modifiersList, GrModifier.VAL,
                              GroovyBundle.message("modifier.0.not.allowed.on.method", GrModifier.VAL), holder);

    checkForAbstractAndFinalCombination(holder, method, modifiersList);

    //script methods
    boolean isMethodAbstract = modifiersList.hasExplicitModifier(PsiModifier.ABSTRACT);
    PsiElement modifierOrList = getModifierOrList(modifiersList, PsiModifier.ABSTRACT);
    if (method.getParent() instanceof GroovyFileBase) {
      if (isMethodAbstract) {
        String message = GroovyBundle.message("script.method.cannot.have.modifier.abstract");
        AnnotationBuilder builder =
          holder.newAnnotation(HighlightSeverity.ERROR, message)
            .range(modifierOrList);
        registerMakeAbstractMethodNotAbstractFix(builder, method, false, message, modifierOrList.getTextRange()).create();
      }

      checkModifierIsNotAllowed(modifiersList, PsiModifier.NATIVE, GroovyBundle.message("script.cannot.have.modifier.native"), holder);
    }
    //type definition methods
    else if (method.getParent() != null && method.getParent().getParent() instanceof GrTypeDefinition containingTypeDef) {

      if (containingTypeDef.isTrait()) {
        checkModifierIsNotAllowed(modifiersList, PsiModifier.PROTECTED, GroovyBundle.message("trait.method.cannot.be.protected"), holder);
      }
      //interface
      else if (containingTypeDef.isInterface()) {
        if (!GroovyConfigUtils.isAtLeastGroovy50(containingTypeDef)) {
          checkModifierIsNotAllowed(modifiersList, PsiModifier.STATIC,
                                    GroovyBundle.message("interface.members.are.not.allowed.to.be", PsiModifier.STATIC), holder);
          checkModifierIsNotAllowed(modifiersList, PsiModifier.PRIVATE,
                                    GroovyBundle.message("interface.members.are.not.allowed.to.be", PsiModifier.PRIVATE), holder);
        }
        checkModifierIsNotAllowed(modifiersList, PsiModifier.PROTECTED,
                                  GroovyBundle.message("interface.members.are.not.allowed.to.be", PsiModifier.PROTECTED), holder);
      }
      else if (containingTypeDef.isAnonymous()) {
        if (isMethodAbstract) {
          String message = GroovyBundle.message("anonymous.class.cannot.have.abstract.method");
          AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(modifierOrList);
          registerMakeAbstractMethodNotAbstractFix(builder, method, false, message, modifierOrList.getTextRange()).create();
        }
      }
      //class
      else {
        PsiModifierList typeDefModifiersList = containingTypeDef.getModifierList();
        LOG.assertTrue(typeDefModifiersList != null, "modifiers list must be not null");

        if (!typeDefModifiersList.hasModifierProperty(PsiModifier.ABSTRACT) && isMethodAbstract) {
          String message = GroovyBundle.message("only.abstract.class.can.have.abstract.method");
          AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(modifiersList);
          registerMakeAbstractMethodNotAbstractFix(builder, method, true, message, modifierOrList.getTextRange()).create();
        }
      }

      if (method.isConstructor()) {
        String message = GroovyBundle.message("constructor.cannot.have.static.modifier");
        checkModifierIsNotAllowed(modifiersList, PsiModifier.STATIC, message, holder);
      }
    }

    if (method.hasModifierProperty(PsiModifier.NATIVE) && method.getBlock() != null) {
      String message = GroovyBundle.message("native.methods.cannot.have.body");
      PsiElement list = getModifierOrList(modifiersList, PsiModifier.NATIVE);
      AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(list);
      GrModifierFix fix = new GrModifierFix(method, PsiModifier.NATIVE, true, false, GrModifierFix.MODIFIER_LIST);
      builder = registerLocalFix(builder, fix, modifiersList, message, ProblemHighlightType.ERROR, list.getTextRange());
      builder.withFix(QuickFixFactory.getInstance().createDeleteMethodBodyFix(method)).create();
    }
  }

  private static void checkForAbstractAndFinalCombination(AnnotationHolder holder, GrMember member, GrModifierList modifiersList) {
    if (member.hasModifierProperty(PsiModifier.FINAL) && member.hasModifierProperty(PsiModifier.ABSTRACT)) {
      String message = GroovyBundle.message("illegal.combination.of.modifiers.abstract.and.final");
      AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(modifiersList);
      GrModifierFix fix1 = new GrModifierFix(member, PsiModifier.FINAL, false, false, GrModifierFix.MODIFIER_LIST);
      builder = registerLocalFix(builder, fix1, modifiersList, message, ProblemHighlightType.ERROR, modifiersList.getTextRange());
      GrModifierFix fix2 = new GrModifierFix(member, PsiModifier.ABSTRACT, false, false, GrModifierFix.MODIFIER_LIST);
      builder = registerLocalFix(builder, fix2, modifiersList, message, ProblemHighlightType.ERROR, modifiersList.getTextRange());
      builder.create();
    }
  }

  private static @NotNull PsiElement getModifierOrList(@NotNull GrModifierList modifiersList,
                                                       @GrModifier.GrModifierConstant String modifier) {
    PsiElement m = modifiersList.getModifier(modifier);
    return m != null ? m : modifiersList;
  }

  private static void checkOverrideAnnotation(AnnotationHolder holder, GrModifierList list, GrMethod method) {
    final PsiAnnotation overrideAnnotation = list.findAnnotation("java.lang.Override");
    if (overrideAnnotation == null) return;
    try {
      MethodSignatureBackedByPsiMethod superMethod = SuperMethodsSearch.search(method, null, true, false).findFirst();
      if (superMethod == null) {
        String message = GroovyBundle.message("method.does.not.override.super");
        holder.newAnnotation(HighlightSeverity.WARNING, message).range(overrideAnnotation).create();
      }
    }
    catch (IndexNotReadyException ignored) {
      //nothing to do
    }
  }

  private static void checkTypeDefinitionModifiers(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    GrModifierList modifiersList = typeDefinition.getModifierList();
    if (modifiersList == null) return;

    checkAccessModifiers(holder, modifiersList, typeDefinition);
    checkDuplicateModifiers(holder, modifiersList, typeDefinition);

    for (PsiClassType classType : typeDefinition.getExtendsListTypes()) {
      PsiClass psiClass = classType.resolve();

      if (psiClass != null && psiClass.hasModifierProperty(PsiModifier.FINAL)) {
        PsiElement identifierGroovy = typeDefinition.getNameIdentifierGroovy();
        String message = GroovyBundle.message("final.class.cannot.be.extended");
        AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(identifierGroovy);
        GrModifierFix fix = new GrModifierFix(typeDefinition, PsiModifier.FINAL, false, false, GrModifierFix.MODIFIER_LIST_OWNER);
        builder = registerLocalFix(builder, fix, typeDefinition, message, ProblemHighlightType.ERROR, identifierGroovy.getTextRange());
        builder.create();
      }
    }

    if (!typeDefinition.isEnum()) {
      checkForAbstractAndFinalCombination(holder, typeDefinition, modifiersList);
    }

    checkModifierIsNotAllowed(modifiersList, PsiModifier.TRANSIENT, holder);
    checkModifierIsNotAllowed(modifiersList, PsiModifier.VOLATILE, holder);
    if (typeDefinition.isEnum()) {
      checkModifierIsNotAllowed(modifiersList, PsiModifier.FINAL, holder);
    }
    if (typeDefinition.isInterface()) {
      checkModifierIsNotAllowed(modifiersList, PsiModifier.FINAL, GroovyBundle.message("interface.cannot.have.modifier.final"), holder);
    }
  }

  private static void checkDuplicateModifiers(AnnotationHolder holder, @NotNull GrModifierList list, PsiElement member) {
    final PsiElement[] modifiers = list.getModifiers();
    if (modifiers.length <= 1) return;
    Set<String> set = new HashSet<>(modifiers.length);
    for (PsiElement modifier : modifiers) {
      if (modifier instanceof GrAnnotation) continue;
      //noinspection MagicConstant
      @GrModifier.GrModifierConstant String name = modifier.getText();
      if (set.contains(name)) {
        String message = GroovyBundle.message("duplicate.modifier", name);
        AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(modifier);
        if (member != null) {
          GrModifierFix fix = new GrRemoveModifierFix(name, GrModifierFix.MODIFIER_LIST);
          builder = registerLocalFix(builder, fix, list, message, ProblemHighlightType.ERROR, list.getTextRange());
        }
        builder.create();
      }
      else {
        set.add(name);
      }
    }
  }

  private static void checkAccessModifiers(AnnotationHolder holder, @NotNull GrModifierList modifierList, PsiMember member) {
    boolean hasPrivate = modifierList.hasExplicitModifier(PsiModifier.PRIVATE);
    boolean hasPublic = modifierList.hasExplicitModifier(PsiModifier.PUBLIC);
    boolean hasProtected = modifierList.hasExplicitModifier(PsiModifier.PROTECTED);

    if (hasPrivate && hasPublic || hasPrivate && hasProtected || hasPublic && hasProtected) {
      String message = GroovyBundle.message("illegal.combination.of.modifiers");
      AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, message).range(modifierList);
      if (hasPrivate) {
        GrModifierFix fix = new GrModifierFix(member, PsiModifier.PRIVATE, false, false, GrModifierFix.MODIFIER_LIST);
        builder = registerLocalFix(builder, fix, modifierList, message, ProblemHighlightType.ERROR, modifierList.getTextRange());
      }
      if (hasProtected) {
        GrModifierFix fix = new GrModifierFix(member, PsiModifier.PROTECTED, false, false, GrModifierFix.MODIFIER_LIST);
        builder = registerLocalFix(builder, fix, modifierList, message, ProblemHighlightType.ERROR, modifierList.getTextRange());
      }
      if (hasPublic) {
        GrModifierFix fix = new GrModifierFix(member, PsiModifier.PUBLIC, false, false, GrModifierFix.MODIFIER_LIST);
        builder = registerLocalFix(builder, fix, modifierList, message, ProblemHighlightType.ERROR, modifierList.getTextRange());
      }
      builder.create();
    }
    else if (member instanceof PsiClass &&
             member.getContainingClass() == null &&
             GroovyConfigUtils.getInstance().isVersionAtLeast(member, GroovyConfigUtils.GROOVY2_0)) {
      String message1 = GroovyBundle.message("top.level.class.may.not.have.private.modifier");
      checkModifierIsNotAllowed(modifierList, PsiModifier.PRIVATE, message1, holder);
      String message2 = GroovyBundle.message("top.level.class.may.not.have.protected.modifier");
      checkModifierIsNotAllowed(modifierList, PsiModifier.PROTECTED, message2, holder);
    }
  }

  private void checkDuplicateMethod(@NotNull GrMethod method) {
    PsiClass clazz = method.getContainingClass();
    if (clazz == null) return;
    GrReflectedMethod[] reflectedMethods = method.getReflectedMethods();
    if (reflectedMethods.length == 0) {
      doCheckDuplicateMethod(method, clazz);
    }
    else {
      for (GrReflectedMethod reflectedMethod : reflectedMethods) {
        doCheckDuplicateMethod(reflectedMethod, clazz);
      }
    }
  }

  private void doCheckDuplicateMethod(@NotNull GrMethod method, @NotNull PsiClass clazz) {
    Set<MethodSignature> duplicatedSignatures = GrClassImplUtil.getDuplicatedSignatures(clazz);
    if (duplicatedSignatures.isEmpty()) return; // optimization

    PsiSubstitutor substitutor = JavaPsiFacade.getElementFactory(method.getProject()).createRawSubstitutor(method);
    MethodSignature signature = method.getSignature(substitutor);
    if (!duplicatedSignatures.contains(signature)) return;

    String signaturePresentation = GroovyPresentationUtil.getSignaturePresentation(signature);
    GrMethod original = method instanceof GrReflectedMethod reflectedMethod ? reflectedMethod.getBaseMethod() : method;
    String message = GroovyBundle.message("method.duplicate", signaturePresentation, clazz.getName());
    myHolder.newAnnotation(HighlightSeverity.ERROR, message).range(GrHighlightUtil.getMethodHeaderTextRange(original)).create();
  }

  private static void checkTypeDefinition(AnnotationHolder holder, @NotNull GrTypeDefinition typeDefinition) {
    if (typeDefinition.isAnonymous()) {
      PsiClass superClass = ((PsiAnonymousClass)typeDefinition).getBaseClassType().resolve();
      if (superClass instanceof GrTypeDefinition definition
          && !GroovyConfigUtils.getInstance().isVersionAtLeast(typeDefinition, GroovyConfigUtils.GROOVY2_5_2) && definition.isTrait()) {
        String message = GroovyBundle.message("anonymous.classes.cannot.be.created.from.traits");
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(typeDefinition.getNameIdentifierGroovy()).create();
      }
    }
    if (typeDefinition.isAnnotationType() && typeDefinition.getContainingClass() != null) {
      String message = GroovyBundle.message("annotation.type.cannot.be.inner");
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(typeDefinition.getNameIdentifierGroovy()).create();
    }

    if (!typeDefinition.hasModifierProperty(PsiModifier.STATIC)
        && (typeDefinition.getContainingClass() != null || typeDefinition instanceof GrAnonymousClassDefinition)) {
      GrTypeDefinition owner = PsiTreeUtil.getParentOfType(typeDefinition, GrTypeDefinition.class);
      if (owner instanceof GrTraitTypeDefinition) {
        String message = GroovyBundle.message("non.static.classes.not.allowed");
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(typeDefinition.getNameIdentifierGroovy()).create();
      }
    }
    checkTypeDefinitionModifiers(holder, typeDefinition);
    checkDuplicateClass(typeDefinition, holder);
    checkCyclicInheritance(holder, typeDefinition);
  }

  private static void checkCyclicInheritance(AnnotationHolder holder, @NotNull GrTypeDefinition typeDefinition) {
    final PsiClass psiClass = InheritanceUtil.getCircularClass(typeDefinition);
    if (psiClass != null) {
      String qname = psiClass.getQualifiedName();
      assert qname != null;
      String message = GroovyBundle.message("cyclic.inheritance.involving.0", qname);
      holder.newAnnotation(HighlightSeverity.ERROR, message).range(GrHighlightUtil.getClassHeaderTextRange(typeDefinition)).create();
    }
  }

  private static void checkForWildCards(AnnotationHolder holder, @Nullable GrReferenceList clause) {
    if (clause == null) return;
    for (GrCodeReferenceElement element : clause.getReferenceElementsGroovy()) {
      final GrTypeArgumentList list = element.getTypeArgumentList();
      if (list != null) {
        for (GrTypeElement type : list.getTypeArgumentElements()) {
          if (type instanceof GrWildcardTypeArgument) {
            String message = GroovyBundle.message("wildcards.are.not.allowed.in.extends.list");
            holder.newAnnotation(HighlightSeverity.ERROR, message).range(type).create();
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
        String message = GroovyBundle.message("duplicate.inner.class", name);
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(typeDefinition.getNameIdentifierGroovy()).create();
      }
    }
    final String qName = typeDefinition.getQualifiedName();
    if (qName != null) {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(typeDefinition.getProject());
      GlobalSearchScope scope = inferClassScopeForSearchingDuplicates(typeDefinition);
      final PsiClass[] classes = facade.findClasses(qName, scope);
      if (classes.length > 1 && (!GroovyConfigUtils.isAtLeastGroovy50(typeDefinition) || areInTheDifferentFiles(classes))) {
        String packageName = getPackageName(typeDefinition);

        String message = !isScriptGeneratedClass(classes)
                         ? GroovyBundle.message("duplicate.class", name, packageName)
                         : GroovyBundle.message("script.generated.with.same.name", qName);
        holder.newAnnotation(HighlightSeverity.ERROR, message).range(typeDefinition.getNameIdentifierGroovy()).create();
      }
    }
  }

  private static boolean areInTheDifferentFiles(PsiClass[] classes) {
    if (classes.length <= 1) return true;

    List<PsiFile> fileList = ContainerUtil.map(classes, it -> it.getContainingFile());
    PsiFile file = fileList.getFirst();
    for (int i = 1; i < fileList.size(); i++) {
      if(fileList.get(i) != file) return true;
    }
    return false;
  }

  private static GlobalSearchScope inferClassScopeForSearchingDuplicates(GrTypeDefinition typeDefinition) {
    GlobalSearchScope defaultScope = typeDefinition.getResolveScope();

    if (typeDefinition.getContainingFile() instanceof GroovyFile file && file.isScript()) {
      Module module = ModuleUtilCore.findModuleForPsiElement(file);
      if (module != null) {
        return defaultScope.intersectWith(module.getModuleScope());
      }
    }
    return defaultScope;
  }

  private static @NlsSafe String getPackageName(GrTypeDefinition typeDefinition) {
    String packageName = "<default package>";
    if (typeDefinition.getContainingFile() instanceof GroovyFile file) {
      final String name = file.getPackageName();
      if (!name.isEmpty()) packageName = name;
    }
    return packageName;
  }

  private static boolean isScriptGeneratedClass(PsiClass[] allClasses) {
    return allClasses.length == 2 && (allClasses[0] instanceof GroovyScriptClass || allClasses[1] instanceof GroovyScriptClass);
  }
}

