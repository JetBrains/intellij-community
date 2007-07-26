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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionData;
import org.jetbrains.plugins.groovy.annotator.intentions.OuterImportsActionCreator;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.bodies.GrClassBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.awt.*;
import java.util.Set;
import java.util.HashSet;

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
    } else if (element instanceof GrVariable) {
      checkVariable(holder, (GrVariable) element);
    } else if (element instanceof GrAssignmentExpression) {
      checkAssignmentExpression((GrAssignmentExpression) element, holder);
    } else if (element instanceof GrNamedArgument) {
      checkCommandArgument((GrNamedArgument) element, holder);
    } else if (element instanceof GrReturnStatement) {
      checkReturnStatement((GrReturnStatement)element, holder);
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
    if (typeDefinition.getParent() instanceof GrClassBody) {
      holder.createErrorAnnotation(typeDefinition.getNameIdentifierGroovy(), "Inner classes are not supported in Groovy");
    }
  }

  private void checkReferenceExpression(AnnotationHolder holder, final GrReferenceExpression refExpr) {
    GroovyResolveResult resolveResult = refExpr.advancedResolve();
    PsiElement element = resolveResult.getElement();
    if (element != null) {
      if (!resolveResult.isAccessible()) {
        String message = GroovyBundle.message("cannot.access", refExpr.getReferenceName());
        holder.createWarningAnnotation(refExpr, message);
      } else if (element instanceof PsiMethod && element.getUserData(GrMethod.BUILDER_METHOD) == null) {
        final PsiMethod method = (PsiMethod) element;
        checkMethodApplicability(method, refExpr, holder);
      }
      if (isAssignmentLHS(refExpr) || element instanceof PsiPackage) return;
    } else {
      if (isAssignmentLHS(refExpr)) return;

      GrExpression qualifier = refExpr.getQualifierExpression();
      if (qualifier == null) {
        GroovyPsiElement context = PsiTreeUtil.getParentOfType(refExpr, GrMethod.class, GrField.class, GrClosableBlock.class);
        if (context instanceof PsiModifierListOwner && ((PsiModifierListOwner) context).hasModifierProperty(PsiModifier.STATIC)) {
          Annotation annotation = holder.createErrorAnnotation(refExpr, GroovyBundle.message("cannot.resolve", refExpr.getReferenceName()));
          annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        } else {
          if (refExpr.getParent() instanceof GrReferenceExpression) {
            Annotation annotation = holder.createWarningAnnotation(refExpr, GroovyBundle.message("cannot.resolve", refExpr.getReferenceName()));
            registerAddImportFixes(refExpr, annotation);
          }
        }
      }
    }

    if (refExpr.getType() == null) {
      PsiElement refNameElement = refExpr.getReferenceNameElement();
      PsiElement elt = refNameElement == null ? refExpr : refNameElement;
      Annotation annotation = holder.createInformationAnnotation(elt,
          GroovyBundle.message("untyped.access", refExpr.getReferenceName()));

      annotation.setEnforcedTextAttributes(new TextAttributes(Color.black, null, Color.black, EffectType.LINE_UNDERSCORE, 0));
    }
  }

  private void checkMethodApplicability(PsiMethod method, GroovyPsiElement place, AnnotationHolder holder) {
    PsiType[] argumentTypes = PsiUtil.getArgumentTypes(place, method.isConstructor());
    if (argumentTypes != null && !PsiUtil.isApplicable(argumentTypes, method)) {
      GroovyPsiElement elementToHighlight = PsiUtil.getArgumentsElement(place);
      if (elementToHighlight == null) {
        elementToHighlight = place;
      }

      //todo more specific error message
      String message = GroovyBundle.message("cannot.apply.method", method.getName());
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

    GrImportStatement importStatement = resolveResult.getImportStatementContext();
    if (importStatement != null) {
      PsiFile file = refElement.getContainingFile();
      if (file instanceof GroovyFile) {
        GroovyInspectionData.getInstance().registerImportUsed(importStatement);
      }
    }

    if (refElement.getReferenceName() != null) {

      if (parent instanceof GrNewExpression) {
        checkNewExpression(holder, (GrNewExpression) parent, resolveResult);
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
      registerAddImportFixes(refElement, annotation);
      //registerCreteClassByTypeFix(refElement, annotation);
      annotation.setHighlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
    } else if (!resolveResult.isAccessible()) {
      String message = GroovyBundle.message("cannot.access", refElement.getReferenceName());
      holder.createErrorAnnotation(refElement, message);
    }
  }

  private void checkNewExpression(AnnotationHolder holder, GrNewExpression newExpression, GroovyResolveResult resolveResult) {
    GrCodeReferenceElement refElement = newExpression.getReferenceElement();
    LOG.assertTrue(refElement != null);
    final PsiElement resolved = resolveResult.getElement();
    if (resolved instanceof PsiMethod) {
      checkMethodApplicability((PsiMethod) resolved, refElement, holder);
    } else if (resolved instanceof PsiClass) {
      //default constructor invocation
      PsiType[] argumentTypes = PsiUtil.getArgumentTypes(refElement, true);
      if (argumentTypes != null && argumentTypes.length > 0) {
        String message = GroovyBundle.message("cannot.find.default.constructor", ((PsiClass) resolved).getName());
        holder.createWarningAnnotation(refElement.getReferenceNameElement(), message);
      }
    } else if (resolved == null && refElement.multiResolve(false).length > 0) {
      final GrArgumentList argList = newExpression.getArgumentList();
      PsiElement toHighlight = argList != null ? argList : refElement.getReferenceNameElement();
      String message = GroovyBundle.message("ambiguous.constructor.call");
      holder.createWarningAnnotation(toHighlight, message);
    } else {
      checkSingleResolvedElement(holder, refElement, resolveResult);
    }
  }

  private void registerAddImportFixes(GrReferenceElement refElement, Annotation annotation) {
    final IntentionAction[] actions = OuterImportsActionCreator.getOuterImportFixes(refElement, refElement.getProject());
    for (IntentionAction action : actions) {
      annotation.registerFix(action);
    }
  }

/*
  private void registerCreateClassByTypeFix(GrReferenceElement refElement, Annotation annotation) {
    annotation.registerFix(CreateClassFix.createClassFixAction(refElement));
  }
*/

}

