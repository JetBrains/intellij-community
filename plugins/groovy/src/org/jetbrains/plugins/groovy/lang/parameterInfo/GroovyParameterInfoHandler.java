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
package org.jetbrains.plugins.groovy.lang.parameterInfo;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.JavaCompletionUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.parameterInfo.*;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class GroovyParameterInfoHandler implements ParameterInfoHandler<GroovyPsiElement, GroovyResolveResult> {
  public boolean couldShowInLookup() {
    return true;
  }

  public Object[] getParametersForLookup(LookupElement item, ParameterInfoContext context) {
    List<? extends PsiElement> elements = JavaCompletionUtil.getAllPsiElements(item);

    if (elements != null) {
      List<GroovyResolveResult> methods = new ArrayList<GroovyResolveResult>();
      for (PsiElement element : elements) {
        if (element instanceof PsiMethod) {
          methods.add(new GroovyResolveResultImpl(element, true));
        }
      }
      return methods.toArray(new Object[methods.size()]);
    }

    return null;
  }

  public Object[] getParametersForDocumentation(GroovyResolveResult resolveResult, ParameterInfoContext context) {
    final PsiElement element = resolveResult.getElement();
    if (element instanceof PsiMethod) {
      return ((PsiMethod) element).getParameterList().getParameters();
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public GroovyPsiElement findElementForParameterInfo(CreateParameterInfoContext context) {
    return findAnchorElement(context.getEditor().getCaretModel().getOffset(), context.getFile());
  }

  public GroovyPsiElement findElementForUpdatingParameterInfo(UpdateParameterInfoContext context) {
    return findAnchorElement(context.getEditor().getCaretModel().getOffset(), context.getFile());
  }

  @Nullable
  private static GroovyPsiElement findAnchorElement(int offset, PsiFile file) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    GroovyPsiElement argList = PsiTreeUtil.getParentOfType(element, GrArgumentList.class);
    if (argList != null) return argList;
    final GrCall call = PsiTreeUtil.getParentOfType(element, GrCall.class);
    if (call != null) {
      argList = call.getArgumentList();
      if (argList != null && argList.getTextRange().contains(element.getTextRange().getStartOffset())) return argList;
    } else {
      offset = CharArrayUtil.shiftBackward(file.getText(), offset, "\n\t ");
      if (offset <= 0) return null;
      element = file.findElementAt(offset);
      if (element != null && element.getParent() instanceof GrReferenceExpression)
        return (GroovyPsiElement) element.getParent();
    }
    return null;
  }

  public void showParameterInfo(@NotNull GroovyPsiElement place, CreateParameterInfoContext context) {
    final PsiElement parent = place.getParent();
    GroovyResolveResult[] variants = GroovyResolveResult.EMPTY_ARRAY;
    if (parent instanceof GrCallExpression) {
      variants = ((GrCallExpression) parent).getMethodVariants();
    } else if (parent instanceof GrConstructorInvocation) {
      final PsiClass clazz = ((GrConstructorInvocation) parent).getDelegatedClass();
      if (clazz != null) {
        final PsiMethod[] constructors = clazz.getConstructors();
        variants = getConstructorResolveResult(constructors, place);
      }
    } else if (parent instanceof GrAnonymousClassDefinition) {
      final PsiElement element = ((GrAnonymousClassDefinition)parent).getBaseClassReferenceGroovy().resolve();
      if (element instanceof PsiClass) {
        final PsiMethod[] constructors = ((PsiClass)element).getConstructors();
        variants = getConstructorResolveResult(constructors, place);
      }
    }
    else if (parent instanceof GrApplicationStatement) {
      final GrExpression funExpr = ((GrApplicationStatement) parent).getFunExpression();
      if (funExpr instanceof GrReferenceExpression) {
        variants = ((GrReferenceExpression) funExpr).getSameNameVariants();
      }
    } else if (place instanceof GrReferenceExpression) {
      variants = ((GrReferenceExpression) place).getSameNameVariants();
    }
    final List<GroovyResolveResult> namedElements = ContainerUtil.findAll(variants, new Condition<GroovyResolveResult>() {
      public boolean value(GroovyResolveResult groovyResolveResult) {
        return groovyResolveResult.getElement() instanceof PsiNamedElement;
      }
    });
    context.setItemsToShow(namedElements.toArray(new Object[namedElements.size()]));
    context.showHint(place, place.getTextRange().getStartOffset(), this);
  }

  private static GroovyResolveResult[] getConstructorResolveResult(PsiMethod[] constructors, PsiElement place) {
    GroovyResolveResult[] variants = new GroovyResolveResult[constructors.length];
    for (int i = 0; i < constructors.length; i++) {
      final boolean isAccessible = com.intellij.psi.util.PsiUtil.isAccessible(constructors[i], place, null);
      variants[i] = new GroovyResolveResultImpl(constructors[i], isAccessible);
    }
    return variants;
  }
  public void updateParameterInfo(@NotNull GroovyPsiElement place, UpdateParameterInfoContext context) {
    int offset = context.getEditor().getCaretModel().getOffset();
    offset = CharArrayUtil.shiftForward(context.getEditor().getDocument().getText(), offset, " \t\n");
    final int currIndex = getCurrentParameterIndex(place, offset);
    context.setCurrentParameter(currIndex);
    final Object[] objects = context.getObjectsToView();

    Outer:
    for (int i = 0; i < objects.length; i++) {
      final GroovyResolveResult resolveResult = (GroovyResolveResult) objects[i];
      PsiNamedElement namedElement = (PsiNamedElement) resolveResult.getElement();
      final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      assert namedElement != null;
      if (!namedElement.isValid()) {
        context.setUIComponentEnabled(i, false);
      } else {
        final PsiType[] constructorTypes = PsiUtil.getArgumentTypes(place, true, false);
        final PsiType[] methodTypes = PsiUtil.getArgumentTypes(place, false, false);
        PsiType[] argTypes = null;
        PsiType[] parameterTypes = null;
        if (namedElement instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod) namedElement;
          PsiParameter[] parameters = method.getParameterList().getParameters();
          parameterTypes = new PsiType[parameters.length];
          for (int j = 0; j < parameters.length; j++) {
            parameterTypes[j] = parameters[j].getType();
          }
          if (resolveResult.getCurrentFileResolveContext() instanceof GrMethodCallExpression)
            parameterTypes = ArrayUtil.remove(parameterTypes, 0);
          argTypes = method.isConstructor() ? constructorTypes : methodTypes;
        } else if (namedElement instanceof GrVariable) {
          final PsiType type = ((GrVariable) namedElement).getTypeGroovy();
          if (type instanceof GrClosureType) {
            argTypes = methodTypes;
            parameterTypes = ((GrClosureType) type).getClosureParameterTypes();
          }
        }
        if (argTypes == null) continue;

        if (parameterTypes.length <= currIndex) {
          context.setUIComponentEnabled(i, false);
          continue;
        } else {
          for (int j = 0; j < currIndex; j++) {
            PsiType argType = argTypes[j];
            final PsiType paramType = substitutor.substitute(parameterTypes[j]);
            if (!TypesUtil.isAssignable(paramType, argType, place.getManager(), place.getResolveScope())) {
              context.setUIComponentEnabled(i, false);
              break Outer;
            }
          }
        }

        context.setUIComponentEnabled(i, true);
      }
    }
  }

  private static int getCurrentParameterIndex(GroovyPsiElement place, int offset) {
    if (place instanceof GrArgumentList) {
      GrArgumentList list = (GrArgumentList) place;
      final GrNamedArgument[] namedArguments = list.getNamedArguments();
      for (GrNamedArgument namedArgument : namedArguments) {
        if (namedArgument.getTextRange().contains(offset)) return 0; //first Map parameter
      }

      int idx = namedArguments.length > 0 ? 1 : 0;

      final GrExpression[] exprs = list.getExpressionArguments();
      for (GrExpression expr : exprs) {
        if (expr.getTextRange().contains(offset)) return idx;
        idx++;
      }
    } 

    return -1;
  }


  public String getParameterCloseChars() {
    return ",){}";
  }

  public boolean tracksParameterIndex() {
    return true;
  }

  public void updateUI(GroovyResolveResult resolveResult, ParameterInfoUIContext context) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();

    PsiNamedElement element = (PsiNamedElement) resolveResult.getElement();
    if (element == null || !element.isValid()) {
      context.setUIComponentEnabled(false);
      return;
    }

    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    StringBuffer buffer = new StringBuffer();

    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) element;
      if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO) {
        if (!method.isConstructor()) {
          PsiType returnType = method.getReturnType();
          if (returnType != null) {
            buffer.append(returnType.getPresentableText());
            buffer.append(" ");
          }
        }
        buffer.append(element.getName());
        buffer.append("(");
      }

      final int currentParameter = context.getCurrentParameterIndex();

      PsiParameter[] parms = method.getParameterList().getParameters();
      if (resolveResult.getCurrentFileResolveContext() instanceof GrMethodCallExpression)
        parms = ArrayUtil.remove(parms, 0);
      int numParams = parms.length;
      if (numParams > 0) {
        final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        for (int j = 0; j < numParams; j++) {
          PsiParameter parm = parms[j];

          int startOffset = buffer.length();

          appendParameterText(parm, substitutor, buffer);

          int endOffset = buffer.length();

          if (j < numParams - 1) {
            buffer.append(", ");
          }

          if (context.isUIComponentEnabled() &&
              (j == currentParameter ||
                  (j == numParams - 1 && parm.isVarArgs() && currentParameter >= numParams)
              )
              ) {
            highlightStartOffset = startOffset;
            highlightEndOffset = endOffset;
          }
        }
      } else {
        buffer.append("no parameters");
      }

      if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO) {
        buffer.append(")");
      }

    } else if (element instanceof PsiClass) {
      buffer.append("no parameters");
    } else if (element instanceof GrVariable) {
      final PsiType type = ((GrVariable) element).getTypeGroovy();
      if (type instanceof GrClosureType) {
        PsiType[] parameterTypes = ((GrClosureType) type).getClosureParameterTypes();
        if (parameterTypes.length > 0) {
          for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) buffer.append(", ");
            PsiType parameterType = ((GrClosureType) type).getClosureParameterTypes()[i];
            buffer.append(parameterType.getPresentableText());
          }
        } else {
          buffer.append("no parameters");
        }
      }
    }

    final boolean isDeprecated = resolveResult instanceof PsiDocCommentOwner && ((PsiDocCommentOwner) resolveResult).isDeprecated();

    context.setupUIComponentPresentation(
        buffer.toString(),
        highlightStartOffset,
        highlightEndOffset,
        !context.isUIComponentEnabled(),
        isDeprecated,
        false,
        context.getDefaultParameterColor()
    );
  }

  private static void appendParameterText(PsiParameter parm, PsiSubstitutor substitutor, StringBuffer buffer) {
    if (parm instanceof GrParameter) {
      buffer.append(GroovyPresentationUtil.getParameterPresentation((GrParameter) parm, substitutor));

      final GrExpression initializer = ((GrParameter) parm).getDefaultInitializer();
      if (initializer != null) {
        buffer.append(" = ").append(initializer.getText());
      }
    } else {
      PsiType t = parm.getType();
      PsiType paramType = substitutor.substitute(t);
      buffer.append(paramType.getPresentableText());
      String name = parm.getName();
      if (name != null) {
        buffer.append(" ");
        buffer.append(name);
      }
    }
  }
}
