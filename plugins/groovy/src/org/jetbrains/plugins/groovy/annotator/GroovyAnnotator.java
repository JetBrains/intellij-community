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

package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.quickfix.AddMethodBodyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateConstructorMatchingSuperFix;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteMethodBodyFix;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.intentions.*;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;
import org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.*;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrScriptField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ast.GrInheritConstructorContributor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.PropertyResolverProcessor;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

import java.util.*;

import static com.intellij.psi.PsiModifier.*;
import static org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter.ANNOTATION;

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
      if (PsiUtil.isCompileStatic(element)) {
        GroovyAssignabilityCheckInspection.checkElement((GroovyPsiElement)element, holder);
      }
      myHolder = null;
    }
    else {
      final PsiElement parent = element.getParent();
      if (parent instanceof GrMethod) {
        if (element.equals(((GrMethod)parent).getNameIdentifierGroovy()) &&
            ((GrMethod)parent).getReturnTypeElementGroovy() == null) {
          checkMethodReturnType((GrMethod)parent, element, holder);
        }
      }
      else if (parent instanceof GrField) {
        final GrField field = (GrField)parent;
        if (element.equals(field.getNameIdentifierGroovy())) {
          final GrAccessorMethod[] getters = field.getGetters();
          for (GrAccessorMethod getter : getters) {
            checkMethodReturnType(getter, field.getNameIdentifierGroovy(), holder);
          }

          final GrAccessorMethod setter = field.getSetter();
          if (setter != null) {
            checkMethodReturnType(setter, field.getNameIdentifierGroovy(), holder);
          }
        }
      }
    }
  }

  @Override
  public void visitTypeArgumentList(GrTypeArgumentList typeArgumentList) {
    PsiElement parent = typeArgumentList.getParent();
    final PsiElement resolved;
    if (parent instanceof GrReferenceElement) {
      resolved = ((GrReferenceElement)parent).resolve();
    }
    else {
      resolved = null;
    }

    if (resolved == null) return;

    if (!(resolved instanceof PsiTypeParameterListOwner)) {
      //myHolder.createErrorAnnotation(typeArgumentList, GroovyBundle.message("type.argument.list.is.no.a"))
      //todo correct error description
      return;
    }

    if (parent instanceof GrCodeReferenceElement) {
      if (!checkDiamonds((GrCodeReferenceElement)parent, myHolder)) return;
    }

    final PsiTypeParameter[] parameters = ((PsiTypeParameterListOwner)resolved).getTypeParameters();
    final GrTypeElement[] arguments = typeArgumentList.getTypeArgumentElements();

    if (arguments.length!=parameters.length) {
      myHolder.createErrorAnnotation(typeArgumentList,
                                     GroovyBundle.message("wrong.number.of.type.arguments", arguments.length, parameters.length));
      return;
    }

    for (int i = 0; i < parameters.length; i++) {
      PsiTypeParameter parameter = parameters[i];
      final PsiClassType[] superTypes = parameter.getExtendsListTypes();
      final PsiType argType = arguments[i].getType();
      for (PsiClassType superType : superTypes) {
        if (!superType.isAssignableFrom(argType)) {
          myHolder.createErrorAnnotation(arguments[i], GroovyBundle.message("type.argument.0.is.not.in.its.bound.should.extend.1", argType.getCanonicalText(), superType.getCanonicalText()));
          break;
        }
      }
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

    final PsiClassType throwable = PsiType.getJavaLangThrowable(statement.getManager(), statement.getResolveScope());

    for (GrCatchClause clause : clauses) {
      final GrParameter parameter = clause.getParameter();
      if (parameter == null) continue;

      final GrTypeElement typeElement = parameter.getTypeElementGroovy();

      PsiType type = typeElement != null ? typeElement.getType() : null;
      if (type == null) {
        type = throwable;
      }

      if (!throwable.isAssignableFrom(type)) {
        LOG.assertTrue(typeElement != null);
        myHolder.createErrorAnnotation(typeElement,
                                       GroovyBundle.message("catch.statement.parameter.type.should.be.a.subclass.of.throwable"));
        continue;
      }

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
                                                                                   types[j].getCanonicalText())).registerFix(new GrRemoveExceptionFix(true));
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

  private boolean checkExceptionUsed(List<PsiType> usedExceptions, GrParameter parameter, GrTypeElement typeElement, PsiType type) {
    for (PsiType exception : usedExceptions) {
      if (exception.isAssignableFrom(type)) {
        myHolder.createWarningAnnotation(typeElement != null ? typeElement : parameter.getNameIdentifierGroovy(),GroovyBundle.message("exception.0.has.already.been.caught", type.getCanonicalText()))
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

  @Override
  public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
    final PsiElement parent = typeDefinition.getParent();
    if (!(typeDefinition.isAnonymous() || parent instanceof GrTypeDefinitionBody || parent instanceof GroovyFile || typeDefinition instanceof GrTypeParameter)) {
      final TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
      final Annotation errorAnnotation =
        myHolder.createErrorAnnotation(range, GroovyBundle.message("class.definition.is.not.expected.here"));
      errorAnnotation.registerFix(new GrMoveClassToCorrectPlaceFix(typeDefinition));
    }
    checkTypeDefinition(myHolder, typeDefinition);

    checkDuplicateMethod(typeDefinition.getMethods(), myHolder);
    checkImplementedMethodsOfClass(myHolder, typeDefinition);
    checkConstructors(myHolder, typeDefinition);
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
        final TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
        holder.createErrorAnnotation(range, GroovyBundle.message("there.is.no.default.constructor.available.in.class.0", qName)).registerFix(new CreateConstructorMatchingSuperFix(typeDefinition));
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
    checkMethodParameters(myHolder, method);

    GrOpenBlock block = method.getBlock();
    if (block != null && TypeInferenceHelper.isTooComplexTooAnalyze(block)) {
      myHolder.createWeakWarningAnnotation(method.getNameIdentifierGroovy(), GroovyBundle.message("method.0.is.too.complex.too.analyze", method.getName()));
    }

    if (method.isConstructor() && method.getContainingClass() instanceof GrAnonymousClassDefinition) {
      myHolder.createErrorAnnotation(method.getNameIdentifierGroovy(), GroovyBundle.message("constructors.are.not.allowed.in.anonymous.class"));
    }

    if (!method.hasModifierProperty(ABSTRACT) && method.getBlock() == null && !method.hasModifierProperty(NATIVE)) {
      final Annotation annotation = myHolder.createErrorAnnotation(method.getNameIdentifierGroovy(), GroovyBundle.message("not.abstract.method.should.have.body"));
      annotation.registerFix(new AddMethodBodyFix(method));
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

  private static void checkMethodParameters(AnnotationHolder holder, GrMethod method) {
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

  private void checkScriptField(GrAnnotation annotation) {
    final PsiAnnotationOwner owner = annotation.getOwner();
    final GrMember container = PsiTreeUtil.getParentOfType(((PsiElement)owner), GrMember.class);
    if (container != null) {
      if (container.getContainingClass() instanceof GroovyScriptClass) {
        myHolder.createErrorAnnotation(annotation, GroovyBundle.message("annotation.field.can.only.be.used.within.a.script.body"));
      }
      else {
        myHolder.createErrorAnnotation(annotation, GroovyBundle.message("annotation.field.can.only.be.used.within.a.script"));
      }
    }
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


    final GrVariable toSearchFor = ResolveUtil.isScriptField(variable) ? GrScriptField.createScriptFieldFrom(variable) : variable;
    PsiNamedElement duplicate = ResolveUtil.resolveExistingElement(variable, new DuplicateVariablesProcessor(toSearchFor), GrReferenceExpression.class, GrVariable.class);
    if (duplicate == null) {
      if (variable instanceof GrParameter) {
        @SuppressWarnings({"ConstantConditions"})
        final PsiElement context = variable.getContext().getContext();
        if (context instanceof GrClosableBlock) {
          duplicate = ResolveUtil.resolveExistingElement((GroovyPsiElement)context, new DuplicateVariablesProcessor(variable),
                                                         GrVariable.class, GrReferenceExpression.class);
        }
        else if (context instanceof GrMethod && !(context.getParent() instanceof GroovyFile)) {
          duplicate = ResolveUtil.resolveExistingElement(((GroovyPsiElement)context.getParent()), new DuplicateVariablesProcessor(variable),
                                                         GrVariable.class, GrReferenceExpression.class);
        }
      }
    }

    if (duplicate instanceof GrLightParameter && "args".equals(duplicate.getName())) {
      duplicate = null;
    }

    if (duplicate instanceof GrVariable) {
      if ((variable instanceof GrField || ResolveUtil.isScriptField(variable)) /*&& duplicate instanceof PsiField*/ ||
          !(duplicate instanceof GrField)) {
        final String key = duplicate instanceof GrField ? "field.already.defined" : "variable.already.defined";
        myHolder.createErrorAnnotation(variable.getNameIdentifierGroovy(), GroovyBundle.message(key, variable.getName()));
      }
    }

    PsiType type = variable.getDeclaredType();
    if (type instanceof PsiEllipsisType && !isLastParameter(variable)) {
      TextRange range = getTypeRange(variable);
      LOG.assertTrue(range != null, variable.getText());
      myHolder.createErrorAnnotation(range, GroovyBundle.message("ellipsis.type.is.not.allowed.here"));
    }
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

  @Override
  public void visitCodeReferenceElement(GrCodeReferenceElement refElement) {
    PsiElement resolved = refElement.resolve();
    if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
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
  }

  private static void checkFieldModifiers(AnnotationHolder holder, GrVariableDeclaration fieldDeclaration) {
    final GrModifierList modifierList = fieldDeclaration.getModifierList();
    final GrField member = (GrField)fieldDeclaration.getVariables()[0];

    checkAccessModifiers(holder, modifierList, member);
    checkDuplicateModifiers(holder, modifierList, member);

    if (modifierList.hasExplicitModifier(VOLATILE) && modifierList.hasExplicitModifier(FINAL)) {
      final Annotation annotation = holder.createErrorAnnotation(modifierList, GroovyBundle.message("illegal.combination.of.modifiers.volatile.and.final"));
      annotation.registerFix(new GrModifierFix(member, modifierList, VOLATILE, true, false));
      annotation.registerFix(new GrModifierFix(member, modifierList, FINAL, true, false));
    }

    checkModifierIsNotAllowed(modifierList, NATIVE, GroovyBundle.message("variable.cannot.be.native"), holder);
    checkModifierIsNotAllowed(modifierList, ABSTRACT, GroovyBundle.message("variable.cannot.be.abstract"), holder);

    if (member.getContainingClass() instanceof GrInterfaceDefinition) {
      checkModifierIsNotAllowed(modifierList, PRIVATE, GroovyBundle.message("interface.members.are.not.allowed.to.be", PRIVATE), holder);
      checkModifierIsNotAllowed(modifierList, PROTECTED, GroovyBundle.message("interface.members.are.not.allowed.to.be", PROTECTED), holder);
    }
  }

  private static void checkModifierIsNotAllowed(GrModifierList modifierList, @ModifierConstant String modifier, String message, AnnotationHolder holder) {
    if (modifierList.hasModifierProperty(modifier)) {
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

  private static void checkMethodReturnType(PsiMethod method, PsiElement toHighlight, AnnotationHolder holder) {
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
  private static String checkSuperMethodSignature(PsiMethod superMethod,
                                                  MethodSignatureBackedByPsiMethod superMethodSignature,
                                                  PsiType superReturnType,
                                                  PsiMethod method,
                                                  MethodSignatureBackedByPsiMethod methodSignature,
                                                  PsiType returnType) {
    if (superReturnType == null) return null;
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

    if (returnType.equals(substitutedSuperReturnType)) return null;
    if (!(returnType instanceof PsiPrimitiveType) &&
        substitutedSuperReturnType.getDeepComponentType() instanceof PsiClassType &&
        TypeConversionUtil.isAssignable(substitutedSuperReturnType, returnType)) {
      return null;
    }

    String qName = getQName(method);
    String baseQName = getQName(superMethod);
    final String presentation = returnType.getCanonicalText()+" "+GroovyPresentationUtil.getSignaturePresentation(methodSignature);
    final String basePresentation = superReturnType.getCanonicalText()+" "+GroovyPresentationUtil.getSignaturePresentation(superMethodSignature);
    return GroovyBundle.message("return.type.is.incompatible", presentation, qName, basePresentation, baseQName);
  }

  @NotNull
  private static String getQName(PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass instanceof PsiAnonymousClass) {
      return GroovyBundle.message("anonymous.class.derived.from") + " " + ((PsiAnonymousClass)aClass).getBaseClassType().getCanonicalText();
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
      else {
        final PsiClass outerClass = clazz.getContainingClass();
        if (com.intellij.psi.util.PsiUtil.isInnerClass(clazz) &&
            outerClass != null &&
            !PsiUtil.hasEnclosingInstanceInScope(outerClass, newExpression, true)) {
          String qname = clazz.getQualifiedName();
          LOG.assertTrue(qname != null, clazz.getText());
          Annotation annotation = myHolder.createErrorAnnotation(refElement, GroovyBundle.message("cannot.reference.nonstatic", qname));
          annotation.setTextAttributes(DefaultHighlighter.UNRESOLVED_ACCESS);
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
   *                            ^
   *                            we are here
   */

  private static boolean followsError(GrClosableBlock closure) {
    PsiElement prev = closure.getPrevSibling();
    return prev instanceof PsiErrorElement || prev instanceof PsiWhiteSpace && prev.getPrevSibling() instanceof PsiErrorElement;
  }

  private static boolean isClosureAmbiguous(GrClosableBlock closure) {
    if (closure.getContainingFile() instanceof GroovyCodeFragment) return false; //for code fragments
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
      if (FINAL.equals(modifierText)) continue;
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


  public void visitAnnotation(GrAnnotation annotation) {
    final GrCodeReferenceElement ref = annotation.getClassReference();
    final PsiElement resolved = ref.resolve();

    if (resolved == null) return;
    assert resolved instanceof PsiClass;

    PsiClass anno = (PsiClass)resolved;
    String qname = anno.getQualifiedName();
    if (!anno.isAnnotationType()) {
      if (qname != null) {
        myHolder.createErrorAnnotation(ref, GroovyBundle.message("class.is.not.annotation", qname));
      }
      return;
    }
    PsiElement parent = annotation.getParent();
    PsiElement owner = parent.getParent();

    final PsiElement ownerToUse = parent instanceof PsiModifierList ? owner : parent;

    String[] elementTypeFields;
    //hack for @Field: Field accepts local vars but our type inferrer thinks that local var with @Field is field itself.
    if (GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD.equals(anno.getQualifiedName()) &&
        ownerToUse instanceof GrVariableDeclaration &&
        GroovyRefactoringUtil.isLocalVariable(((GrVariableDeclaration)ownerToUse).getVariables()[0])) {
      elementTypeFields = new String[]{"LOCAL_VARIABLE"};
    }
    else {
      elementTypeFields = GrAnnotationImpl.getApplicableElementTypeFields(ownerToUse);
    }
    if (elementTypeFields != null && !GrAnnotationImpl.isAnnotationApplicableTo(annotation, false, elementTypeFields)) {
      String description = JavaErrorMessages
        .message("annotation.not.applicable", ref.getText(), JavaErrorMessages.message("annotation.target." + elementTypeFields[0]));
      myHolder.createErrorAnnotation(ref, description);
    }

    if (GroovyCommonClassNames.GROOVY_TRANSFORM_FIELD.equals(qname)) {
      checkScriptField(annotation);
    }
  }

  @Override
  public void visitAnnotationArgumentList(GrAnnotationArgumentList annotationArgumentList) {
    final GrAnnotation annotation = (GrAnnotation)annotationArgumentList.getParent();

    final PsiClass anno = ResolveUtil.resolveAnnotation(annotationArgumentList);
    if (anno == null) return;


    if ("groovy.lang.Newify".equals(anno.getQualifiedName()) && annotation.getParameterList().getAttributes().length == 0) {
      return;
    }

    final GrAnnotationNameValuePair[] attributes = annotationArgumentList.getAttributes();

    Set<String> usedAttrs = new HashSet<String>();
    if (attributes.length == 1 && attributes[0].getNameIdentifierGroovy() == null) {
      checkAnnotationValue(anno, attributes[0], "value", usedAttrs, attributes[0].getValue());
    }
    else {
      for (GrAnnotationNameValuePair attribute : attributes) {
        final PsiElement identifier = attribute.getNameIdentifierGroovy();
        final String name = identifier.getText();
        checkAnnotationValue(anno, identifier, name, usedAttrs, attribute.getValue());
      }
    }

    List<String> missedAttrs = new ArrayList<String>();
    final PsiMethod[] methods = anno.getMethods();
    for (PsiMethod method : methods) {
      final String name = method.getName();
      if (usedAttrs.contains(name) ||
          method instanceof PsiAnnotationMethod && ((PsiAnnotationMethod)method).getDefaultValue() != null) {
        continue;
      }
       missedAttrs.add(name);
    }

    if (!missedAttrs.isEmpty()) {
      myHolder.createErrorAnnotation(annotation.getClassReference(),
                                     GroovyBundle.message("missed.attributes", StringUtil.join(missedAttrs, ", ")));
    }
  }

  private void checkAnnotationValue(@NotNull PsiClass anno,
                                    @NotNull PsiElement identifierToHighlight,
                                    @NotNull String name,
                                    @NotNull Set<String> usedAttrs,
                                    @Nullable GrAnnotationMemberValue value) {
    if (usedAttrs.contains(name)) {
      myHolder.createErrorAnnotation(identifierToHighlight, GroovyBundle.message("duplicate.attribute"));
    }

    usedAttrs.add(name);

    final PsiMethod[] methods = anno.findMethodsByName(name, false);
    if (methods.length == 0) {
      myHolder.createErrorAnnotation(identifierToHighlight,
                                     GroovyBundle.message("at.interface.0.does.not.contain.attribute", anno.getQualifiedName(), name));
    }
    else {
      final PsiMethod method = methods[0];
      final PsiType ltype = method.getReturnType();
      if (ltype != null && value != null) {
        checkAnnotationValueByType(value, ltype, true);
      }
    }
  }

  @Override
  public void visitDefaultAnnotationValue(GrDefaultAnnotationValue defaultAnnotationValue) {
    final GrAnnotationMemberValue value = defaultAnnotationValue.getDefaultValue();
    if (value==null) return;

    final PsiElement parent = defaultAnnotationValue.getParent();
    assert parent instanceof GrAnnotationMethod;
    final PsiType type = ((GrAnnotationMethod)parent).getReturnType();

    checkAnnotationValueByType(value, type, false);
  }

  private void checkAnnotationValueByType(@NotNull GrAnnotationMemberValue value, @Nullable PsiType ltype, boolean skipArrays) {
    final GlobalSearchScope resolveScope = value.getResolveScope();
    final PsiManager manager = value.getManager();

    if (value instanceof GrExpression) {
      final PsiType rtype;
      if (value instanceof GrClosableBlock) {
        rtype = PsiType.getJavaLangClass(manager, resolveScope);
      }
      else {
        rtype = ((GrExpression)value).getType();
      }

      if (rtype != null && !checkAnnoTypeAssignable(ltype, resolveScope, manager, rtype, skipArrays)) {
        myHolder.createErrorAnnotation(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
      }
    }

    else if (value instanceof GrAnnotation) {
      final PsiElement resolved = ((GrAnnotation)value).getClassReference().resolve();
      if (resolved instanceof PsiClass) {
        final PsiClassType rtype = JavaPsiFacade.getElementFactory(value.getProject()).createType((PsiClass)resolved, PsiSubstitutor.EMPTY);
        if (!checkAnnoTypeAssignable(ltype, resolveScope, manager, rtype, skipArrays)) {
          myHolder.createErrorAnnotation(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
        }
      }
    }

    else if (value instanceof GrAnnotationArrayInitializer) {

      if (ltype instanceof PsiArrayType) {
        final PsiType componentType = ((PsiArrayType)ltype).getComponentType();
        final GrAnnotationMemberValue[] initializers = ((GrAnnotationArrayInitializer)value).getInitializers();
        for (GrAnnotationMemberValue initializer : initializers) {
          checkAnnotationValueByType(initializer, componentType, false);
        }
      }
      else {
        final PsiType rtype = TypesUtil.getTupleByAnnotationArrayInitializer((GrAnnotationArrayInitializer)value);
        if (!checkAnnoTypeAssignable(ltype, resolveScope, manager, rtype, skipArrays)) {
          myHolder.createErrorAnnotation(value, GroovyBundle.message("cannot.assign", rtype.getPresentableText(), ltype.getPresentableText()));
        }
      }
    }
  }

  private static boolean checkAnnoTypeAssignable(@Nullable PsiType type, @NotNull GlobalSearchScope resolveScope, @NotNull PsiManager manager, @Nullable PsiType rtype, boolean skipArrays) {
    rtype = TypesUtil.unboxPrimitiveTypeWrapper(rtype);
    if (TypesUtil.isAssignableByMethodCallConversion(type, rtype, manager, resolveScope)) return true;

    if (!(type instanceof PsiArrayType && skipArrays)) return false;

    final PsiType componentType = ((PsiArrayType)type).getComponentType();
    return checkAnnoTypeAssignable(componentType, resolveScope, manager, rtype, skipArrays);
  }

  @Override
  public void visitImportStatement(GrImportStatement importStatement) {
    checkAnnotationList(myHolder, importStatement.getAnnotationList(), GroovyBundle.message("import.statement.cannot.have.modifiers"));
  }

  @Override
  public void visitExtendsClause(GrExtendsClause extendsClause) {
    GrTypeDefinition typeDefinition = (GrTypeDefinition)extendsClause.getParent();

    if (typeDefinition.isInterface()) {
      checkReferenceList(myHolder, extendsClause, true, GroovyBundle.message("no.class.expected.here"), null);
    }
    else if (typeDefinition.isEnum()) {
      myHolder.createErrorAnnotation(extendsClause, GroovyBundle.message("enums.may.not.have.extends.clause"));
    }
    else {
      checkReferenceList(myHolder, extendsClause, false, GroovyBundle.message("no.interface.expected.here"), new ChangeExtendsImplementsQuickFix(typeDefinition));
    }

    checkForWildCards(myHolder, extendsClause);
  }

  @Override
  public void visitImplementsClause(GrImplementsClause implementsClause) {
    GrTypeDefinition typeDefinition = (GrTypeDefinition)implementsClause.getParent();

    if (typeDefinition.isInterface()) {
      myHolder.createErrorAnnotation(implementsClause, GroovyBundle.message("no.implements.clause.allowed.for.interface"));
    }
    else {
      checkReferenceList(myHolder, implementsClause, true, GroovyBundle.message("no.class.expected.here"), new ChangeExtendsImplementsQuickFix(typeDefinition));
    }

    checkForWildCards(myHolder, implementsClause);
  }

  private static void checkReferenceList(@NotNull AnnotationHolder holder,
                                         @NotNull GrReferenceList list,
                                         boolean interfaceExpected,
                                         @NotNull String message,
                                         @Nullable IntentionAction fix) {
    for (GrCodeReferenceElement refElement : list.getReferenceElements()) {
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
            holder.createInfoAnnotation(nameElement, null).setTextAttributes(DefaultHighlighter.KEYWORD);
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
    if (typeDefinition.hasModifierProperty(ABSTRACT)) return;
    if (typeDefinition.isAnnotationType()) return;
    if (typeDefinition instanceof GrTypeParameter) return;

    Collection<CandidateInfo> collection = OverrideImplementUtil.getMethodsToOverrideImplement(typeDefinition, true);
    if (collection.isEmpty()) return;

    final PsiElement element = collection.iterator().next().getElement();
    assert element instanceof PsiNamedElement;
    String notImplementedMethodName = ((PsiNamedElement)element).getName();

    final TextRange range = GrHighlightUtil.getClassHeaderTextRange(typeDefinition);
    final Annotation annotation = holder.createErrorAnnotation(range,
                                                               GroovyBundle.message("method.is.not.implemented", notImplementedMethodName));
    registerImplementsMethodsFix(typeDefinition, annotation);
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
      final Annotation annotation = holder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.abstract.and.final"));
      annotation.registerFix(new GrModifierFix(method, modifiersList, FINAL, false, false));
      annotation.registerFix(new GrModifierFix(method, modifiersList, ABSTRACT, false, false));
    }

    //script methods
    boolean isMethodAbstract = modifiersList.hasExplicitModifier(ABSTRACT);
    if (method.getParent() instanceof GroovyFileBase) {
      if (isMethodAbstract) {
        final Annotation annotation = holder.createErrorAnnotation(getModifierOrList(modifiersList, ABSTRACT), GroovyBundle.message("script.method.cannot.have.modifier.abstract"));
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
          checkModifierIsNotAllowed(modifiersList, PROTECTED, GroovyBundle.message("interface.members.are.not.allowed.to.be", PROTECTED), holder);
        }
        else if (containingTypeDef.isAnonymous()) {
          //anonymous class
          checkModifierIsNotAllowed(modifiersList, STATIC, GroovyBundle.message("static.declaration.in.inner.class"), holder);

          if (isMethodAbstract) {
            final Annotation annotation = holder.createErrorAnnotation(getModifierOrList(modifiersList, ABSTRACT), GroovyBundle.message("anonymous.class.cannot.have.abstract.method"));
            registerMakeAbstractMethodNotAbstractFix(annotation, method, false);
          }
        }
        //class
        else {
          PsiModifierList typeDefModifiersList = containingTypeDef.getModifierList();
          LOG.assertTrue(typeDefModifiersList != null, "modifiers list must be not null");

          if (!typeDefModifiersList.hasExplicitModifier(ABSTRACT) && isMethodAbstract) {
            final Annotation annotation = holder.createErrorAnnotation(modifiersList, GroovyBundle.message("only.abstract.class.can.have.abstract.method"));
            registerMakeAbstractMethodNotAbstractFix(annotation, method, true);
          }
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
        final Annotation annotation = holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), GroovyBundle.message("final.class.cannot.be.extended"));
        annotation.registerFix(new GrModifierFix(typeDefinition, modifiersList, FINAL, false, false));
      }
    }

    if (modifiersList.hasModifierProperty(ABSTRACT) && modifiersList.hasModifierProperty(FINAL)) {
      final Annotation annotation = holder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.abstract.and.final"));
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
      return modifierList.hasExplicitModifier(PUBLIC) ||
             modifierList.hasExplicitModifier(PROTECTED) ||
             modifierList.hasExplicitModifier(PRIVATE);
    }

    @Override
    public boolean execute(@NotNull PsiElement element, ResolveState state) {
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

