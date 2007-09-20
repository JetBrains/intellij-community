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

package org.jetbrains.plugins.groovy.annotator;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.grails.perspectives.DomainClassUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.annotator.gutter.OverrideGutter;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassFix;
import org.jetbrains.plugins.groovy.annotator.intentions.OuterImportsActionCreator;
import org.jetbrains.plugins.groovy.codeInspection.GroovyImportsTracker;
import org.jetbrains.plugins.groovy.highlighter.DefaultHighlighter;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.overrideImplement.quickFix.ImplementMethodsQuickFix;

import java.util.*;

/**
 * @author ven
 */
public class GroovyAnnotator implements Annotator {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.GroovyAnnotator");

  private GroovyAnnotator() {
  }

  public static final GroovyAnnotator INSTANCE = new GroovyAnnotator();

  public void annotate(PsiElement element, AnnotationHolder holder) {
    if (element instanceof GrCodeReferenceElement) {
      checkReferenceElement(holder, (GrCodeReferenceElement) element);
    } else if (element instanceof GrReferenceExpression) {
      checkReferenceExpression(holder, (GrReferenceExpression) element);
    } else if (element instanceof GrTypeDefinition) {
      checkTypeDefinition(holder, (GrTypeDefinition) element);
      checkTypeDefinitionModifiers(holder, (GrTypeDefinition) element);
      checkDuplicateMethod(((GrTypeDefinition) element).getBody().getMethods(), holder);
      checkImplementedMethodsOfClass(holder, (GrTypeDefinition) element);
    } else if (element instanceof GrMethod) {
      checkMethodDefinitionModifiers(holder, (GrMethod) element);
      checkInnerMethod(holder, (GrMethod) element);
      addOverrideGutter(holder, (GrMethod) element);
    } else if (element instanceof GrVariableDeclaration) {
      checkVariableDeclaration(holder, (GrVariableDeclaration) element);
    } else if (element instanceof GrVariable) {
      checkVariable(holder, (GrVariable) element);
    } else if (element instanceof GrAssignmentExpression) {
      checkAssignmentExpression((GrAssignmentExpression) element, holder);
    } else if (element instanceof GrNamedArgument) {
      checkCommandArgument((GrNamedArgument) element, holder);
    } else if (element instanceof GrReturnStatement) {
      checkReturnStatement((GrReturnStatement) element, holder);
    } else if (element instanceof GrListOrMap) {
      checkMap(((GrListOrMap) element).getNamedArguments(), holder);
    } else if (element instanceof GrNewExpression) {
      checkNewExpression(holder, (GrNewExpression) element);
    } else if (element instanceof GrConstructorInvocation) {
      checkConstructorInvocation(holder, (GrConstructorInvocation) element);
    } else if (element instanceof GroovyFile) {
      final GroovyFile file = (GroovyFile) element;
      if (file.isScript()) {
        checkScriptDuplicateMethod(file.getTopLevelDefinitions(), holder);
      }
      if (DomainClassUtils.isDomainClass(element.getContainingFile().getVirtualFile())) {
        checkDomainClass((GroovyFile) element, holder);
      }
    } else if (!(element instanceof PsiWhiteSpace) && element.getContainingFile() instanceof GroovyFile) {
      GroovyImportsTracker.getInstance(element.getProject()).markFileAnnotated((GroovyFile) element.getContainingFile());
    }
  }

  private void checkImplementedMethodsOfClass(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (typeDefinition.hasModifierProperty(PsiModifier.ABSTRACT)) return;

    final Collection<CandidateInfo> methodsToImplement = OverrideImplementUtil.getMethodsToOverrideImplement(typeDefinition, true);

    if (methodsToImplement.isEmpty()) return;

    final PsiElement methodCandidateInfo = methodsToImplement.toArray(CandidateInfo.EMPTY_ARRAY)[0].getElement();
    assert methodCandidateInfo instanceof PsiMethod;
    
    String notImplementedMethodName = ((PsiMethod) methodCandidateInfo).getName();

    final int startOffset = typeDefinition.getTextRange().getStartOffset();
    final GrTypeDefinitionBody body = typeDefinition.getBody();
    int endOffset = body != null ? body.getTextRange().getStartOffset() : typeDefinition.getTextRange().getEndOffset();
    final Annotation annotation = holder.createErrorAnnotation(new TextRange(startOffset, endOffset), GroovyBundle.message("method.is.not.implemented", notImplementedMethodName));
    registerImplementsMethodsFix(typeDefinition, annotation);
  }

  private void registerImplementsMethodsFix(GrTypeDefinition typeDefinition, Annotation annotation) {
    annotation.registerFix(new ImplementMethodsQuickFix(typeDefinition));
  }

  private void addOverrideGutter(AnnotationHolder holder, GrMethod method) {
    final Annotation annotation = holder.createInfoAnnotation(method, null);

    final PsiMethod[] superMethods = method.findSuperMethods();
    if (superMethods.length > 0) {
      boolean isImplements = !method.hasModifierProperty(PsiModifier.ABSTRACT) && superMethods[0].hasModifierProperty(PsiModifier.ABSTRACT);
      annotation.setGutterIconRenderer(new OverrideGutter(superMethods, isImplements));
    }
  }

  private void checkConstructorInvocation(AnnotationHolder holder, GrConstructorInvocation invocation) {
    final GroovyResolveResult resolveResult = invocation.resolveConstructorGenerics();
    if (resolveResult != null) {
      checkMethodApplicability(resolveResult, invocation.getThisOrSuperKeyword(), holder);
    } else {
      final GroovyResolveResult[] results = invocation.multiResolveConstructor();
      final GrArgumentList argList = invocation.getArgumentList();
      PsiElement toHighlight = argList != null ? argList : invocation;
      if (results.length > 0) {
        String message = GroovyBundle.message("ambiguous.constructor.call");
        holder.createWarningAnnotation(toHighlight, message);
      } else {
        final PsiClass clazz = invocation.getDelegatedClass();
        if (clazz != null) {
          //default constructor invocation
          PsiType[] argumentTypes = PsiUtil.getArgumentTypes(invocation.getThisOrSuperKeyword(), true);
          if (argumentTypes != null && argumentTypes.length > 0) {
            String message = GroovyBundle.message("cannot.find.default.constructor", clazz.getName());
            holder.createWarningAnnotation(toHighlight, message);
          }
        }
      }
    }
  }

  private void checkInnerMethod(AnnotationHolder holder, GrMethod grMethod) {
    if (grMethod.getParent() instanceof GrOpenBlock)
      holder.createErrorAnnotation(grMethod, GroovyBundle.message("Inner.methods.are.not.support"));
  }

  private void checkDomainClass(GroovyFile file, AnnotationHolder holder) {
    DomainClassAnnotator domainClassAnnotator = new DomainClassAnnotator();
    domainClassAnnotator.annotate(file, holder);
  }

  private void checkMap(GrNamedArgument[] namedArguments, AnnotationHolder holder) {
    final Map<GrNamedArgument, List<GrNamedArgument>> map = factorDuplicates(namedArguments, new TObjectHashingStrategy<GrNamedArgument>() {
      public int computeHashCode(GrNamedArgument arg) {
        final GrArgumentLabel label = arg.getLabel();
        if (label == null) return 0;
        return label.getName().hashCode();
      }

      public boolean equals(GrNamedArgument arg1, GrNamedArgument arg2) {
        final GrArgumentLabel label1 = arg1.getLabel();
        final GrArgumentLabel label2 = arg2.getLabel();
        if (label1 == null || label2 == null) {
          return label1 == null && label2 == null;
        }

        return label1.getName().equals(label2.getName());
      }
    });

    processDuplicates(map, holder);
  }

  protected void processDuplicates(Map<GrNamedArgument, List<GrNamedArgument>> map, AnnotationHolder holder) {
    for (List<GrNamedArgument> args : map.values()) {
      for (int i = 1; i < args.size(); i++) {
        GrNamedArgument namedArgument = args.get(i);
        holder.createErrorAnnotation(namedArgument, GroovyBundle.message("duplicate.element.in.the.map"));
      }
    }
  }

  private void checkVariableDeclaration(AnnotationHolder holder, GrVariableDeclaration variableDeclaration) {

    PsiElement parent = variableDeclaration.getParent();
    assert parent != null;

    PsiElement typeDef = parent.getParent();
    if (typeDef != null && typeDef instanceof GrTypeDefinition) {
      PsiModifierList modifiersList = variableDeclaration.getModifierList();
      checkAccessModifiers(holder, modifiersList);

      if (modifiersList.hasExplicitModifier(PsiModifier.VOLATILE)
          && modifiersList.hasExplicitModifier(PsiModifier.FINAL)) {
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.volatile.and.final"));
      }

      if (modifiersList.hasExplicitModifier(PsiModifier.NATIVE)) {
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("variable.cannot.be.native"));
      }

      if (modifiersList.hasExplicitModifier(PsiModifier.ABSTRACT)) {
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("variable.cannot.be.abstract"));
      }
    }
  }

  private void checkMethodDefinitionModifiers(AnnotationHolder holder, GrMethod grMethod) {
    final PsiModifierList modifiersList = grMethod.getModifierList();
    checkAccessModifiers(holder, modifiersList);

    //script methods
    boolean isMethodAbstract = modifiersList.hasExplicitModifier(PsiModifier.ABSTRACT);
    boolean isMethodStatic = modifiersList.hasExplicitModifier(PsiModifier.STATIC);
    if (grMethod.getParent() instanceof GroovyFileBase) {
      if (isMethodAbstract) {
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("script.cannot.have.modifier.abstract"));
      }

      if (modifiersList.hasExplicitModifier(PsiModifier.NATIVE)) {
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("script.cannot.have.modifier.native"));
      }
    } else  //type definition methods
      if (grMethod.getParent() != null && grMethod.getParent().getParent() instanceof GrTypeDefinition) {
        GrTypeDefinition containingTypeDef = ((GrTypeDefinition) grMethod.getParent().getParent());

        //interface
        if (containingTypeDef.isInterface()) {
          if (modifiersList.hasExplicitModifier(PsiModifier.STATIC)) {
            holder.createErrorAnnotation(modifiersList, GroovyBundle.message("interface.must.have.no.static.method"));
          }

          if (modifiersList.hasExplicitModifier(PsiModifier.PRIVATE)) {
            holder.createErrorAnnotation(modifiersList, GroovyBundle.message("interface.must.have.no.private.method"));
          }

        } else if (containingTypeDef.isEnum()) {
          //enumeration
          //todo
        } else if (containingTypeDef.isAnnotationType()) {
          //annotation
          //todo
        } else {
          //class
          PsiModifierList typeDefModifiersList = containingTypeDef.getModifierList();
          LOG.assertTrue(typeDefModifiersList != null, "modifiers list must be not null");

          if (!typeDefModifiersList.hasExplicitModifier(PsiModifier.ABSTRACT)) {
            if (isMethodAbstract) {
              holder.createErrorAnnotation(modifiersList, GroovyBundle.message("not.abstract.class.cannot.have.abstract.method"));
            }
          } else {
            if (isMethodStatic) {
              holder.createErrorAnnotation(modifiersList, GroovyBundle.message("abstract.class.cannot.have.static.method"));
            }
          }

          if (!isMethodAbstract) {
            if (grMethod.getBlock() == null) {
              holder.createErrorAnnotation(grMethod.getNameIdentifierGroovy(), GroovyBundle.message("not.abstract.method.should.have.body"));
            }
          }

        }
      }
  }

  private void checkTypeDefinitionModifiers(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    PsiModifierList modifiersList = typeDefinition.getModifierList();

    if (modifiersList == null) return;

    /**** class ****/
    checkAccessModifiers(holder, modifiersList);

    PsiClassType[] extendsListTypes = typeDefinition.getExtendsListTypes();

    for (PsiClassType classType : extendsListTypes) {
      PsiClass psiClass = classType.resolve();

      if (psiClass != null) {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList != null) {
          if (modifierList.hasExplicitModifier(PsiModifier.FINAL)) {
            holder.createErrorAnnotation(modifiersList, GroovyBundle.message("final.class.cannot.be.extended"));
          }
        }
      }
    }

    if (modifiersList.hasExplicitModifier(PsiModifier.ABSTRACT)
        && modifiersList.hasExplicitModifier(PsiModifier.FINAL)) {
      holder.createErrorAnnotation(modifiersList, GroovyBundle.message("illegal.combination.of.modifiers.abstract.and.final"));
    }

    if (modifiersList.hasExplicitModifier(PsiModifier.TRANSIENT)) {
      holder.createErrorAnnotation(modifiersList, GroovyBundle.message("modifier.transient.not.allowed.here"));
    }
    if (modifiersList.hasExplicitModifier(PsiModifier.VOLATILE)) {
      holder.createErrorAnnotation(modifiersList, GroovyBundle.message("modifier.volatile.not.allowed.here"));
    }

    /**** interface ****/
    if (typeDefinition.isInterface()) {
      if (modifiersList.hasExplicitModifier(PsiModifier.FINAL)) {
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("intarface.cannot.have.modifier.final"));
      }

      if (modifiersList.hasExplicitModifier(PsiModifier.VOLATILE)) {
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("modifier.volatile.not.allowed.here"));
      }

      if (modifiersList.hasExplicitModifier(PsiModifier.TRANSIENT)) {
        holder.createErrorAnnotation(modifiersList, GroovyBundle.message("modifier.transient.not.allowed.here"));
      }
    }
  }

  private void checkAccessModifiers(AnnotationHolder holder, @NotNull PsiModifierList modifierList) {
    boolean hasPrivate = modifierList.hasExplicitModifier(PsiModifier.PRIVATE);
    boolean hasPublic = modifierList.hasExplicitModifier(PsiModifier.PUBLIC);
    boolean hasProtected = modifierList.hasExplicitModifier(PsiModifier.PROTECTED);

    if (hasPrivate && hasPublic
        || hasPrivate && hasProtected
        || hasPublic && hasProtected) {
      holder.createErrorAnnotation(modifierList, GroovyBundle.message("illegal.combination.of.modifiers"));
    }
  }

  private void checkScriptDuplicateMethod(GrTopLevelDefintion[] topLevelDefinitions, AnnotationHolder holder) {
    List<GrMethod> methods = new ArrayList<GrMethod>();

    for (GrTopLevelDefintion topLevelDefinition : topLevelDefinitions) {
      if (topLevelDefinition instanceof GrMethod) {
        methods.add(((GrMethod) topLevelDefinition));
      }
    }

    checkDuplicateMethod(methods.toArray(new GrMethod[methods.size()]), holder);
  }

  private void checkDuplicateMethod(GrMethod[] methods, AnnotationHolder holder) {
    final Map<GrMethod, List<GrMethod>> map = factorDuplicates(methods, new TObjectHashingStrategy<GrMethod>() {
      public int computeHashCode(GrMethod method) {
        return method.getSignature(PsiSubstitutor.EMPTY).hashCode();
      }

      public boolean equals(GrMethod method1, GrMethod method2) {
        return method1.getSignature(PsiSubstitutor.EMPTY).equals(method2.getSignature(PsiSubstitutor.EMPTY));
      }
    });
    processMethodDuplicates(map, holder);
  }

  protected void processMethodDuplicates(Map<GrMethod, List<GrMethod>> map, AnnotationHolder holder) {
    HashSet<GrMethod> duplicateMethodsWarning = new HashSet<GrMethod>();
    HashSet<GrMethod> duplicateMethodsErrors = new HashSet<GrMethod>();

    for (GrMethod method : map.keySet()) {
      List<GrMethod> duplicateMethods = map.get(method);

      if (duplicateMethods != null && duplicateMethods.size() > 1) {
        HashMap<PsiType, GrMethod> duplicateMethodsToReturnTypeMap = new HashMap<PsiType, GrMethod>();

        for (GrMethod duplicateMethod : duplicateMethods) {
          GrTypeElement typeElement = duplicateMethod.getReturnTypeElementGroovy();

          PsiType methodReturnType;
          if (typeElement != null) {
            methodReturnType = typeElement.getType();
          } else {
            methodReturnType = PsiType.NULL;
          }

          duplicateMethodsWarning.add(duplicateMethod);

          GrMethod grMethodWithType = duplicateMethodsToReturnTypeMap.get(methodReturnType);
          if (grMethodWithType != null) {
            duplicateMethodsErrors.add(duplicateMethod);
            duplicateMethodsErrors.add(grMethodWithType);
            duplicateMethodsWarning.remove(duplicateMethod);
            duplicateMethodsWarning.remove(grMethodWithType);
          }

          duplicateMethodsToReturnTypeMap.put(methodReturnType, duplicateMethod);
        }
      }
    }

    for (GrMethod duplicateMethod : duplicateMethodsErrors) {
      holder.createErrorAnnotation(duplicateMethod.getNameIdentifierGroovy(), GroovyBundle.message("repetitive.method.name.signature.and.return.type"));
    }

    for (GrMethod duplicateMethod : duplicateMethodsWarning) {
      holder.createWarningAnnotation(duplicateMethod.getNameIdentifierGroovy(), GroovyBundle.message("repetitive.method.name.signature"));
    }
  }


  private void checkReturnStatement(GrReturnStatement returnStatement, AnnotationHolder holder) {
    final GrExpression value = returnStatement.getReturnValue();
    if (value != null) {
      final PsiType type = value.getType();
      if (type != null) {
        final GrMethod method = PsiTreeUtil.getParentOfType(returnStatement, GrMethod.class);
        if (method != null) {
          if (method.isConstructor()) {
            holder.createErrorAnnotation(value, GroovyBundle.message("cannot.return.from.constructor"));
          } else {
            final PsiType returnType = method.getReturnType();
            if (returnType != null) {
              if (PsiType.VOID.equals(returnType)) {
                holder.createErrorAnnotation(value, GroovyBundle.message("cannot.return.from.void.method"));
              } else {
                checkAssignability(holder, returnType, type, value);
              }
            }
          }
        }
      }
    }
  }

  private void checkCommandArgument(GrNamedArgument namedArgument, AnnotationHolder holder) {
    final GrArgumentLabel label = namedArgument.getLabel();
    if (label != null) {
      PsiType expectedType = label.getExpectedArgumentType();
      if (expectedType != null) {
        expectedType = TypeConversionUtil.erasure(expectedType);
        final GrExpression expr = namedArgument.getExpression();
        if (expr != null) {
          final PsiType argType = expr.getType();
          if (argType != null) {
            final PsiClassType listType = namedArgument.getManager().getElementFactory().createTypeByFQClassName("java.util.List", namedArgument.getResolveScope());
            if (listType.isAssignableFrom(argType)) return; //this is constructor arguments list
            checkAssignability(holder, expectedType, argType, namedArgument);
          }
        }
      }
    }
  }

  private void checkAssignmentExpression(GrAssignmentExpression assignment, AnnotationHolder holder) {
    IElementType opToken = assignment.getOperationToken();
    if (opToken == GroovyTokenTypes.mASSIGN) {
      GrExpression lValue = assignment.getLValue();
      GrExpression rValue = assignment.getRValue();
      if (lValue != null && rValue != null) {
        PsiType lType = lValue.getType();
        PsiType rType = rValue.getType();
        if (lType != null && rType != null) {
          checkAssignability(holder, lType, rType, rValue);
        }
      }
    }
  }

  private void checkVariable(AnnotationHolder holder, GrVariable variable) {
    final GrVariable duplicate = ResolveUtil.resolveDuplicateLocalVariable(variable);
    if (duplicate != null) {
      if (duplicate instanceof GrField && !(variable instanceof GrField)) {
        holder.createWarningAnnotation(variable.getNameIdentifierGroovy(), GroovyBundle.message("field.already.defined", variable.getName()));
      } else {
        final String key = duplicate instanceof GrField ? "field.already.defined" : "variable.already.defined";
        holder.createErrorAnnotation(variable.getNameIdentifierGroovy(), GroovyBundle.message(key, variable.getName()));
      }
    }

    PsiType varType = variable.getType();
    GrExpression initializer = variable.getInitializerGroovy();
    if (initializer != null) {
      PsiType rType = initializer.getType();
      if (rType != null) {
        checkAssignability(holder, varType, rType, initializer);
      }
    }
  }

  private void checkAssignability(AnnotationHolder holder, @NotNull PsiType lType, @NotNull PsiType rType, GroovyPsiElement element) {
    if (!TypesUtil.isAssignable(lType, rType, element.getManager(), element.getResolveScope())) {
      holder.createWarningAnnotation(element, GroovyBundle.message("cannot.assign", rType.getInternalCanonicalText(), lType.getInternalCanonicalText()));
    }
  }

  private void checkTypeDefinition(AnnotationHolder holder, GrTypeDefinition typeDefinition) {
    if (GroovyElementTypes.CLASS_BODY.equals(typeDefinition.getNode().getTreeParent().getElementType())) {
      holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), "Inner classes are not supported in Groovy");
    }

    //TODO: add quickfix to change implements -> extends or class to interface 
    final GrImplementsClause implementsClause = typeDefinition.getImplementsClause();
    if (implementsClause != null) {
      checkForImplementingInterface(holder, implementsClause);
    }

    final GrExtendsClause extendsClause = typeDefinition.getExtendsClause();
    if (extendsClause != null) {
      checkForExtendingClass(holder, extendsClause);
    }
  }

  private boolean checkForExtendingClass(AnnotationHolder holder, GrExtendsClause extendsClause) {
    final GrCodeReferenceElement[] extendsList = extendsClause.getReferenceElements();
    if (extendsList.length != 0) {
      for (GrCodeReferenceElement extendsElement : extendsList) {
        final PsiElement extClass = extendsElement.resolve();
        if (extClass == null || !(extClass instanceof PsiClass)) return false;

        if (((PsiClass) extClass).isInterface()) {
          holder.createErrorAnnotation(extendsElement, GroovyBundle.message("interface.is.not.expected.here"));
        }
      }
    }

    return true;
  }

  private boolean checkForImplementingInterface(AnnotationHolder holder, GrImplementsClause implementsClause) {
    final GrCodeReferenceElement[] implementsList = implementsClause.getReferenceElements();

    if (implementsList.length != 0) {
      for (GrCodeReferenceElement implementElement : implementsList) {
        final PsiElement implClass = implementElement.resolve();
        if (implClass == null || !(implClass instanceof PsiClass)) return false;

        if (!((PsiClass) implClass).isInterface()) {
          holder.createErrorAnnotation(implementElement, GroovyBundle.message("interface.expected.here"));
        }
      }
    }
    return true;
  }

  private void checkReferenceExpression(AnnotationHolder holder, final GrReferenceExpression refExpr) {
    GroovyResolveResult resolveResult = refExpr.advancedResolve();
    registerUsedImport(refExpr, resolveResult);
    PsiElement resolved = resolveResult.getElement();
    if (resolved != null) {
      if (!resolveResult.isAccessible()) {
        String message = GroovyBundle.message("cannot.access", refExpr.getReferenceName());
        holder.createWarningAnnotation(refExpr, message);
      } else if (resolved instanceof PsiMethod && resolved.getUserData(GrMethod.BUILDER_METHOD) == null) {
        checkMethodApplicability(resolveResult, refExpr, holder);
      }
      if (isAssignmentLHS(refExpr) || resolved instanceof PsiPackage) return;
    } else {
      if (isAssignmentLHS(refExpr)) return;

      GrExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier == null) {
        GroovyPsiElement context = PsiTreeUtil.getParentOfType(refExpr, GrMethod.class, GrField.class, GrClosableBlock.class);
        if (context instanceof PsiModifierListOwner && ((PsiModifierListOwner) context).hasModifierProperty(PsiModifier.STATIC)) {
          Annotation annotation = holder.createErrorAnnotation(refExpr, GroovyBundle.message("cannot.resolve", refExpr.getReferenceName()));
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
          return;
        }
      }
    }

    if (refExpr.getType() == null) {
      PsiElement refNameElement = refExpr.getReferenceNameElement();
      PsiElement elt = refNameElement == null ? refExpr : refNameElement;
      Annotation annotation = holder.createInformationAnnotation(elt,
          GroovyBundle.message("untyped.access", refExpr.getReferenceName()));
      if (resolved == null && refExpr.getQualifierExpression() == null) {
        if (!(refExpr.getParent() instanceof GrCallExpression)) {
          registerCreateClassByTypeFix(refExpr, annotation, false);
        }
        registerAddImportFixes(refExpr, annotation);
      }

      //annotation.setEnforcedTextAttributes(new TextAttributes(Color.black, null, Color.black, EffectType.LINE_UNDERSCORE, 0));
      annotation.setTextAttributes(DefaultHighlighter.UNTYPED_ACCESS);
    }
  }

  private void registerUsedImport(GrReferenceElement referenceElement, GroovyResolveResult resolveResult) {
    GrImportStatement importStatement = resolveResult.getImportStatementContext();
    if (importStatement != null) {
      PsiFile file = referenceElement.getContainingFile();
      if (file instanceof GroovyFile) {
        GroovyImportsTracker importsTracker = GroovyImportsTracker.getInstance(referenceElement.getProject());
        importsTracker.registerImportUsed(importStatement);
      }
    }
  }

  private void checkMethodApplicability(GroovyResolveResult methodResolveResult, PsiElement place, AnnotationHolder holder) {
    final PsiMethod method = (PsiMethod) methodResolveResult.getElement();
    assert method != null;
    PsiType[] argumentTypes = PsiUtil.getArgumentTypes(place, method.isConstructor());
    if (argumentTypes != null && !PsiUtil.isApplicable(argumentTypes, method, methodResolveResult.getSubstitutor())) {
      PsiElement elementToHighlight = PsiUtil.getArgumentsElement(place);
      if (elementToHighlight == null) {
        elementToHighlight = place;
      }

      final String typesString = buildArgTypesList(argumentTypes);
      String message;
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null) {
        final PsiClassType containingType = method.getManager().getElementFactory().createType(containingClass, methodResolveResult.getSubstitutor());
        message = GroovyBundle.message("cannot.apply.method1", method.getName(), containingType.getInternalCanonicalText(), typesString);
      } else {
        message = GroovyBundle.message("cannot.apply.method", method.getName(), typesString);
      }
      holder.createWarningAnnotation(elementToHighlight, message);
    }
  }

  private boolean isAssignmentLHS(GrReferenceExpression refExpr) {
    return refExpr.getParent() instanceof GrAssignmentExpression &&
        refExpr.equals(((GrAssignmentExpression) refExpr.getParent()).getLValue());
  }

  private void checkReferenceElement(AnnotationHolder holder, final GrCodeReferenceElement refElement) {
    final PsiElement parent = refElement.getParent();
    GroovyResolveResult resolveResult = refElement.advancedResolve();
    registerUsedImport(refElement, resolveResult);
    if (refElement.getReferenceName() != null) {

      if (parent instanceof GrImportStatement &&
          ((GrImportStatement) parent).isStatic() &&
          refElement.multiResolve(false).length > 0) {
        return;
      }

      checkSingleResolvedElement(holder, refElement, resolveResult);
    }

  }

  private void checkSingleResolvedElement(AnnotationHolder holder, GrCodeReferenceElement refElement, GroovyResolveResult resolveResult) {
    final PsiElement resolved = resolveResult.getElement();
    if (resolved == null) {
      String message = GroovyBundle.message("cannot.resolve", refElement.getReferenceName());

      // Register quickfix
      final Annotation annotation = holder.createErrorAnnotation(refElement, message);
      // todo implement for nested classes
      if (refElement.getQualifier() == null) {
        registerCreateClassByTypeFix(refElement, annotation, false);
        registerAddImportFixes(refElement, annotation);
      }
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
    } else if (!resolveResult.isAccessible()) {
      String message = GroovyBundle.message("cannot.access", refElement.getReferenceName());
      holder.createErrorAnnotation(refElement, message);
    }
  }

  private void checkNewExpression(AnnotationHolder holder, GrNewExpression newExpression) {
    if (newExpression.getArrayCount() > 0) return;
    GrCodeReferenceElement refElement = newExpression.getReferenceElement();
    if (refElement == null) return;
    final GroovyResolveResult resolveResult = newExpression.resolveConstructorGenerics();
    if (resolveResult != null) {
      checkMethodApplicability(resolveResult, refElement, holder);
    } else {
      final GroovyResolveResult[] results = newExpression.multiResolveConstructor();
      final GrArgumentList argList = newExpression.getArgumentList();
      PsiElement toHighlight = argList != null ? argList : refElement.getReferenceNameElement();
      if (results.length > 0) {
        String message = GroovyBundle.message("ambiguous.constructor.call");
        holder.createWarningAnnotation(toHighlight, message);
      } else {
        final PsiElement element = refElement.resolve();
        if (element instanceof PsiClass) {
          //default constructor invocation
          PsiType[] argumentTypes = PsiUtil.getArgumentTypes(refElement, true);
          if (argumentTypes != null && argumentTypes.length > 0) {
            String message = GroovyBundle.message("cannot.find.default.constructor", ((PsiClass) element).getName());
            holder.createWarningAnnotation(toHighlight, message);
          }
        }
      }
    }
  }

  private void registerAddImportFixes(GrReferenceElement refElement, Annotation annotation) {
    final IntentionAction[] actions = OuterImportsActionCreator.getOuterImportFixes(refElement, refElement.getProject());
    for (IntentionAction action : actions) {
      annotation.registerFix(action);
    }
  }

  private void registerCreateClassByTypeFix(GrReferenceElement refElement, Annotation annotation, boolean createConstructor) {
    annotation.registerFix(CreateClassFix.createClassFixAction(refElement, createConstructor));
  }


  public static <D extends GroovyPsiElement> Map<D, List<D>> factorDuplicates(D[] elements, TObjectHashingStrategy<D> strategy) {
    if (elements == null || elements.length == 0) return Collections.emptyMap();

    THashMap<D, List<D>> map = new THashMap<D, List<D>>(strategy);

    for (D element : elements) {
      List<D> list = map.get(element);
      if (list == null) {
        list = new ArrayList<D>();
      }
      list.add(element);
      map.put(element, list);
    }

    return map;
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
}

