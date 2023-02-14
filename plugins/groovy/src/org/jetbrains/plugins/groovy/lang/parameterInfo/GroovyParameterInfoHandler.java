// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parameterInfo;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithTabActionSupport;
import com.intellij.lang.parameterInfo.ParameterInfoUIContext;
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.documentation.TypePresentation;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrInnerClassConstructorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

public class GroovyParameterInfoHandler implements ParameterInfoHandlerWithTabActionSupport<GroovyPsiElement, Object, GroovyPsiElement> {
  private static final Logger LOG = Logger.getInstance(GroovyParameterInfoHandler.class);

  private static final Set<Class<?>> ourStopSearch = Collections.singleton(GrMethod.class);

  @NotNull
  @Override
  public Set<? extends Class<?>> getArgListStopSearchClasses() {
    return ourStopSearch;
  }


  @Override
  public GroovyPsiElement findElementForParameterInfo(@NotNull CreateParameterInfoContext context) {
    GroovyPsiElement place = findAnchorElement(context.getEditor().getCaretModel().getOffset(), context.getFile());
    if (place == null) {
      return null;
    }
    final List<Object> itemsToShow = collectParameterInfo(place);
    context.setItemsToShow(ArrayUtil.toObjectArray(itemsToShow));
    return place;
  }

  @Override
  public GroovyPsiElement findElementForUpdatingParameterInfo(@NotNull UpdateParameterInfoContext context) {
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

  @Override
  public void showParameterInfo(@NotNull GroovyPsiElement place, @NotNull CreateParameterInfoContext context) {
    context.showHint(place, place.getTextRange().getStartOffset(), this);
  }

  @NotNull
  @RequiresBackgroundThread
  private static List<Object> collectParameterInfo(@NotNull GroovyPsiElement place) {
    GroovyResolveResult[] variants = ResolveUtil.getCallVariants(place);

    final List<Object> elementToShow = new ArrayList<>();
    final PsiElement parent = place.getParent();
    if (parent instanceof GrMethodCall) {
      final GrExpression invoked = ((GrMethodCall)parent).getInvokedExpression();
      if (isPropertyOrVariableInvoked(invoked)) {
        final PsiType type = invoked.getType();
        if (type instanceof GrClosureType) {
          addSignatureVariant(elementToShow, (GrClosureType)type);
        }
        else if (type != null) {
          addMethodAndClosureVariants(elementToShow,
                                      ResolveUtil.getMethodCandidates(type, "call", invoked, PsiUtil.getArgumentTypes(place, true)));
        }
      }
      else {
        addMethodAndClosureVariants(elementToShow, variants);
      }
    }
    else {
      elementToShow.addAll(Arrays.asList(variants));
    }

    filterOutReflectedMethods(elementToShow);
    return elementToShow;
  }

  private static void addMethodAndClosureVariants(@NotNull List<Object> elementToShow, GroovyResolveResult @NotNull [] variants) {
    for (GroovyResolveResult variant : variants) {
      final PsiElement element = variant.getElement();
      if (element instanceof PsiMethod) {
        elementToShow.add(variant);
      }
      else if (element instanceof GrVariable) {
        final PsiType type = ((GrVariable)element).getTypeGroovy();
        if (type instanceof GrClosureType) {
          addSignatureVariant(elementToShow, (GrClosureType)type);
        }
      }
    }
  }

  private static void addSignatureVariant(@NotNull final List<Object> elementToShow, @NotNull GrClosureType type) {
    elementToShow.addAll(type.getSignatures());
  }

  private static void filterOutReflectedMethods(List toShow) {
    Set<GrMethod> methods = new HashSet<>();

    for (Iterator iterator = toShow.iterator(); iterator.hasNext(); ) {
      Object next = iterator.next();
      if (next instanceof GroovyResolveResult) {
        final PsiElement element = ((GroovyResolveResult)next).getElement();
        if (element instanceof GrReflectedMethod) {
          final GrMethod base = ((GrReflectedMethod)element).getBaseMethod();
          if (!methods.add(base)) {
            iterator.remove();
          }
        }
      }
    }
  }

  private static boolean isPropertyOrVariableInvoked(GrExpression invoked) {
    if (!(invoked instanceof GrReferenceExpression)) return false;

    final GroovyResolveResult resolveResult = ((GrReferenceExpression)invoked).advancedResolve();
    return resolveResult.isInvokedOnProperty() || resolveResult.getElement() instanceof PsiVariable;
  }

  @Override
  public void updateParameterInfo(@NotNull GroovyPsiElement place, @NotNull UpdateParameterInfoContext context) {
    int offset = context.getEditor().getCaretModel().getOffset();
    offset = CharArrayUtil.shiftForward(context.getEditor().getDocument().getText(), offset, " \t\n");
    final int currIndex = getCurrentParameterIndex(place, offset);
    context.setCurrentParameter(currIndex);
    final Object[] objects = context.getObjectsToView();

    Outer:
    for (int i = 0; i < objects.length; i++) {
      PsiType[] parameterTypes = null;
      PsiType[] argTypes = null;
      PsiSubstitutor substitutor = null;
      if (objects[i] instanceof GroovyResolveResult resolveResult) {
        PsiNamedElement namedElement = (PsiNamedElement)resolveResult.getElement();
        if (namedElement instanceof GrReflectedMethod) namedElement = ((GrReflectedMethod)namedElement).getBaseMethod();

        substitutor = resolveResult.getSubstitutor();
        assert namedElement != null;
        if (!namedElement.isValid()) {
          context.setUIComponentEnabled(i, false);
          continue Outer;
        }
        if (namedElement instanceof PsiMethod method) {
          PsiParameter[] parameters = method.getParameterList().getParameters();
          parameters = updateConstructorParams(method, parameters, context.getParameterOwner());
          parameterTypes = PsiType.createArray(parameters.length);
          for (int j = 0; j < parameters.length; j++) {
            parameterTypes[j] = parameters[j].getType();
          }
          argTypes = PsiUtil.getArgumentTypes(place, false);
        }
        if (argTypes == null) continue;
      }
      else if (objects[i] instanceof GrSignature signature) {
        argTypes = PsiUtil.getArgumentTypes(place, false);
        parameterTypes = PsiType.createArray(signature.getParameterCount());
        int j = 0;
        for (GrClosureParameter parameter : signature.getParameters()) {
          parameterTypes[j++] = parameter.getType();
        }
      }
      else {
        continue Outer;
      }

      assert argTypes != null;
      if (argTypes.length > currIndex) {
        if (parameterTypes.length <= currIndex) {
          context.setUIComponentEnabled(i, false);
          continue;
        }
        else {
          for (int j = 0; j < currIndex; j++) {
            PsiType argType = argTypes[j];
            final PsiType paramType = substitutor != null ? substitutor.substitute(parameterTypes[j]) : parameterTypes[j];
            if (!TypesUtil.isAssignableByMethodCallConversion(paramType, argType, place)) {
              context.setUIComponentEnabled(i, false);
              break Outer;
            }
          }
        }
      }

      context.setUIComponentEnabled(i, true);
    }
  }

  private static int getCurrentParameterIndex(GroovyPsiElement place, int offset) {
    if (place instanceof GrArgumentList list) {

      int idx = (list.getNamedArguments().length > 0) ? 1 : 0;
      for (PsiElement child = list.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child.getTextRange().contains(offset)) {
          if (child instanceof GrNamedArgument) return 0;
          return idx;
        }

        if (child.getNode().getElementType() == GroovyTokenTypes.mCOMMA) idx++;
        if (isNamedArgWithPriorComma(child)) idx--;
      }
    }
    return -1;
  }

  private static boolean isNamedArgWithPriorComma(PsiElement child) {
    if (!(child instanceof GrNamedArgument)) return false;
    final PsiElement element = PsiUtil.skipWhitespacesAndComments(child.getPrevSibling(), false);
    return element != null && element.getNode().getElementType() == GroovyTokenTypes.mCOMMA;
  }

  @Override
  public void updateUI(Object o, @NotNull ParameterInfoUIContext context) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();

    if (o == null) return;

    Object element;
    if (o instanceof GroovyResolveResult) {
      element = ((GroovyResolveResult)o).getElement();
      if (element == null || !((PsiElement)element).isValid()) {
        context.setUIComponentEnabled(false);
        return;
      }
    }
    else if (o instanceof GrSignature) {
      if (!((GrSignature)o).isValid()) {
        context.setUIComponentEnabled(false);
        return;
      }
      element = o;
    }
    else {
      return;
    }

    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    final int currentParameter = context.getCurrentParameterIndex();

    StringBuilder buffer = new StringBuilder();


    if (element instanceof PsiMethod method) {
      if (method instanceof GrReflectedMethod) method = ((GrReflectedMethod)method).getBaseMethod();

      if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO) {
        if (!method.isConstructor()) {
          PsiType returnType = PsiUtil.getSmartReturnType(method);
          if (returnType != null) {
            buffer.append(returnType.getPresentableText());
            buffer.append(' ');
          }
        }
        buffer.append(method.getName());
        buffer.append('(');
      }

      PsiParameter[] params = method.getParameterList().getParameters();

      params = updateConstructorParams(method, params, context.getParameterOwner());

      int numParams = params.length;
      if (numParams > 0) {
        LOG.assertTrue(o instanceof GroovyResolveResult, o.getClass());
        final PsiSubstitutor substitutor = ((GroovyResolveResult)o).getSubstitutor();
        for (int j = 0; j < numParams; j++) {
          PsiParameter param = params[j];

          int startOffset = buffer.length();

          appendParameterText(param, substitutor, buffer);

          int endOffset = buffer.length();

          if (j < numParams - 1) {
            buffer.append(", ");
          }

          if (context.isUIComponentEnabled() &&
              (j == currentParameter || (j == numParams - 1 && param.isVarArgs() && currentParameter >= numParams))) {
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
    }
    else if (element instanceof GrSignature) {
      GrClosureParameter[] parameters = ((GrSignature)element).getParameters();
      if (parameters.length > 0) {
        for (int i = 0; i < parameters.length; i++) {
          if (i > 0) buffer.append(", ");

          int startOffset = buffer.length();
          final PsiType psiType = parameters[i].getType();
          if (psiType == null) {
            buffer.append("def");
          }
          else {
            buffer.append(psiType.getPresentableText());
          }
          buffer.append(' ').append(parameters[i].getName() != null ? parameters[i].getName() : "<unknown>");

          int endOffset = buffer.length();

          if (context.isUIComponentEnabled() &&
              (i == currentParameter || (i == parameters.length - 1 && ((GrSignature)element).isVarargs() && currentParameter >= parameters.length))) {
            highlightStartOffset = startOffset;
            highlightEndOffset = endOffset;
          }

          final GrExpression initializer = parameters[i].getDefaultInitializer();
          if (initializer != null) {
            buffer.append(" = ").append(initializer.getText());
          }
        }
      }
      else {
        buffer.append("no parameters");
      }
    }

    final boolean isDeprecated = o instanceof PsiDocCommentOwner && ((PsiDocCommentOwner) o).isDeprecated();

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

  private static PsiParameter[] updateConstructorParams(PsiMethod method, PsiParameter[] params, PsiElement place) {
    if (GrInnerClassConstructorUtil.isInnerClassConstructorUsedOutsideOfItParent(method, place)) {
      GrMethod grMethod = (GrMethod)method;
      params = GrInnerClassConstructorUtil
        .addEnclosingInstanceParam(grMethod, method.getContainingClass().getContainingClass(), grMethod.getParameters(), true);
    }
    return params;
  }

  private static void appendParameterText(PsiParameter param, PsiSubstitutor substitutor, StringBuilder buffer) {
    if (param instanceof GrParameter grParam) {
      GroovyPresentationUtil.appendParameterPresentation(grParam, substitutor, TypePresentation.PRESENTABLE, buffer, false);

      final GrExpression initializer = grParam.getInitializerGroovy();
      if (initializer != null) {
        buffer.append(" = ").append(initializer.getText());
      }
      else if (grParam.isOptional()) {
        String defaultValue = PsiTypesUtil.getDefaultValueOfType(param.getType());
        buffer.append(" = ").append(defaultValue);
      }
    } else {
      PsiType t = param.getType();
      PsiType paramType = substitutor.substitute(t);
      buffer.append(paramType.getPresentableText());
      String name = param.getName();
      buffer.append(" ");
      buffer.append(name);
    }
  }

  @Override
  public GroovyPsiElement @NotNull [] getActualParameters(@NotNull GroovyPsiElement o) {
    if (o instanceof GrArgumentList) return ((GrArgumentList)o).getAllArguments();
    return GroovyPsiElement.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public IElementType getActualParameterDelimiterType() {
    return GroovyTokenTypes.mCOMMA;
  }

  @NotNull
  @Override
  public IElementType getActualParametersRBraceType() {
    return GroovyTokenTypes.mRPAREN;
  }

  private static final Set<Class<?>> ALLOWED_PARAM_CLASSES = Collections.singleton(GroovyPsiElement.class);

  @NotNull
  @Override
  public Set<Class<?>> getArgumentListAllowedParentClasses() {
    return ALLOWED_PARAM_CLASSES;
  }

  @NotNull
  @Override
  public Class<GroovyPsiElement> getArgumentListClass() {
    return GroovyPsiElement.class;
  }
}
