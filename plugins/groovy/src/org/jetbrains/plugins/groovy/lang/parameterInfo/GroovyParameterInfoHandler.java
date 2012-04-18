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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyPresentationUtil;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

/**
 * @author ven
 */
public class GroovyParameterInfoHandler implements ParameterInfoHandlerWithTabActionSupport<GroovyPsiElement, Object, GroovyPsiElement> {
  private static final Logger LOG = Logger.getInstance(GroovyParameterInfoHandler.class);

  public boolean couldShowInLookup() {
    return true;
  }

  private static final Set<? extends Class> ourStopSearch = Collections.singleton(GrMethod.class);

  @NotNull
  @Override
  public Set<? extends Class> getArgListStopSearchClasses() {
    return ourStopSearch;
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
      return ArrayUtil.toObjectArray(methods);
    }

    return null;
  }

  public Object[] getParametersForDocumentation(Object resolveResult, ParameterInfoContext context) {
    if (resolveResult instanceof GroovyResolveResult) {
      final PsiElement element = ((GroovyResolveResult)resolveResult).getElement();
      if (element instanceof PsiMethod) {
        return ((PsiMethod)element).getParameterList().getParameters();
      }
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

  @SuppressWarnings("unchecked")
  public void showParameterInfo(@NotNull GroovyPsiElement place, CreateParameterInfoContext context) {
    GroovyResolveResult[] variants = ResolveUtil.getCallVariants(place);

    final List elementToShow = new ArrayList();
    final PsiElement parent = place.getParent();
    if (parent instanceof GrMethodCall) {
      final Condition<GroovyResolveResult> methodsOrClosures = new Condition<GroovyResolveResult>() {
        public boolean value(GroovyResolveResult result) {
          final PsiElement element = result.getElement();
          return element instanceof PsiMethod && !result.isInvokedOnProperty() ||
                 element instanceof GrVariable && ((GrVariable)element).getTypeGroovy() instanceof GrClosureType;
        }
      };
      final GrExpression invoked = ((GrMethodCall)parent).getInvokedExpression();
      if (isPropertyOrVariableInvoked(invoked)) {
        final PsiType type = invoked.getType();
        if (type instanceof GrClosureType) {
          elementToShow.add(type);
        }
        else if (type != null) {
          final GroovyResolveResult[] calls = ResolveUtil.getMethodCandidates(type, "call", place, PsiUtil.getArgumentTypes(place, true));
          elementToShow.addAll(ContainerUtil.findAll(calls, methodsOrClosures));
        }
      }
      else {
        elementToShow.addAll(ContainerUtil.findAll(variants, methodsOrClosures));
      }
    }
    else {
      elementToShow.addAll(Arrays.asList(variants));
    }
    
    filterOutReflectedMethods(elementToShow);
    context.setItemsToShow(ArrayUtil.toObjectArray(elementToShow));
    context.showHint(place, place.getTextRange().getStartOffset(), this);
  }

  private static void filterOutReflectedMethods(List toShow) {
    Set<GrMethod> methods = new HashSet<GrMethod>();

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

  public void updateParameterInfo(@NotNull GroovyPsiElement place, UpdateParameterInfoContext context) {
    final PsiElement parameterOwner = context.getParameterOwner();
    if (parameterOwner != place) {
      context.removeHint();
      return;
    }

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
      if (objects[i] instanceof GroovyResolveResult) {
        final GroovyResolveResult resolveResult = (GroovyResolveResult)objects[i];
        PsiNamedElement namedElement = (PsiNamedElement)resolveResult.getElement();
        if (namedElement instanceof GrReflectedMethod) namedElement = ((GrReflectedMethod)namedElement).getBaseMethod();

        substitutor = resolveResult.getSubstitutor();
        assert namedElement != null;
        if (!namedElement.isValid()) {
          context.setUIComponentEnabled(i, false);
          continue Outer;
        }
        if (namedElement instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)namedElement;
          PsiParameter[] parameters = method.getParameterList().getParameters();
          parameterTypes = new PsiType[parameters.length];
          for (int j = 0; j < parameters.length; j++) {
            parameterTypes[j] = parameters[j].getType();
          }
          argTypes = PsiUtil.getArgumentTypes(place, false);
        }
        else if (namedElement instanceof GrVariable) {
          final PsiType type = ((GrVariable)namedElement).getTypeGroovy();
          if (type instanceof GrClosureType) {
            argTypes = PsiUtil.getArgumentTypes(place, false);
            parameterTypes = ((GrClosureType)type).getClosureParameterTypes();
          }
        }
        if (argTypes == null) continue;
      }
      else if (objects[i] instanceof GrClosureType) {
        final GrClosureType type = (GrClosureType)objects[i];
        argTypes = PsiUtil.getArgumentTypes(place, false);
        parameterTypes = type.getClosureParameterTypes();
      }
      else {
        continue Outer;
      }

      assert parameterTypes != null;
      assert argTypes != null;

      if (parameterTypes.length <= currIndex) {
        context.setUIComponentEnabled(i, false);
        continue;
      }
      else {
        for (int j = 0; j < currIndex; j++) {
          PsiType argType = argTypes[j];
          final PsiType paramType = substitutor != null ? substitutor.substitute(parameterTypes[j]) : parameterTypes[j];
          if (!TypesUtil.isAssignable(paramType, argType, place)) {
            context.setUIComponentEnabled(i, false);
            break Outer;
          }
        }
      }

      context.setUIComponentEnabled(i, true);
    }
  }

  private static int getCurrentParameterIndex(GroovyPsiElement place, int offset) {
    if (place instanceof GrArgumentList) {
      GrArgumentList list = (GrArgumentList)place;
      final GrNamedArgument[] namedArguments = list.getNamedArguments();
      for (GrNamedArgument namedArgument : namedArguments) {
        if (getArgRange(namedArgument).contains(offset)) return 0; //first Map parameter
      }

      int idx = namedArguments.length > 0 ? 1 : 0;

      final GrExpression[] exprs = list.getExpressionArguments();
      for (GrExpression expr : exprs) {
        if (getArgRange(expr).contains(offset)) return idx;
        idx++;
      }

      if (exprs.length == 0 || getArgRange(exprs[exprs.length - 1]).getEndOffset() <= offset) {
        return idx;
      }
      else {
        return 0;
      }
    }

    return -1;
  }

  private static TextRange getArgRange(PsiElement arg) {
    PsiElement cur = arg;
    int end;
    int start;
    do {
      PsiElement sibling = cur.getNextSibling();
      if (sibling == null) {
        end = cur.getTextRange().getEndOffset();
        break;
      }
      else {
        cur = sibling;
      }
      IElementType type = cur.getNode().getElementType();
      if (GroovyTokenTypes.mCOMMA.equals(type) || GroovyTokenTypes.mRPAREN.equals(type)) {
        end = cur.getTextRange().getStartOffset();
        break;
      }
    }
    while (true);

    do {
      PsiElement sibling = cur.getPrevSibling();
      if (sibling == null) {
        start = cur.getTextRange().getStartOffset();
        break;
      }
      else {
        cur = sibling;
      }
      IElementType type = cur.getNode().getElementType();
      if (GroovyTokenTypes.mCOMMA.equals(type) || GroovyTokenTypes.mLPAREN.equals(type)) {
        start = cur.getTextRange().getEndOffset();
        break;
      }
    }
    while (true);

    return new TextRange(start, end + 1);
  }


  public String getParameterCloseChars() {
    return ",){}";
  }

  public boolean tracksParameterIndex() {
    return true;
  }

  public void updateUI(Object o, ParameterInfoUIContext context) {
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
    else if (o instanceof GrClosureType) {
      if (!((GrClosureType)o).isValid()) {
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

    StringBuilder buffer = new StringBuilder();


    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
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

      final int currentParameter = context.getCurrentParameterIndex();

      final PsiParameter[] params = method.getParameterList().getParameters();

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
              (j == currentParameter ||
                  (j == numParams - 1 && param.isVarArgs() && currentParameter >= numParams)
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
    }
    else if (element instanceof GrVariable) {
      final PsiElement parent = context.getParameterOwner().getParent();
      if (parent == null || !parent.isValid()) {
        context.setUIComponentEnabled(false);
        return;
      }
      final PsiType type;
      if (parent instanceof GrMethodCallExpression) {
        type = ((GrMethodCallExpression)parent).getInvokedExpression().getType();
      }
      else {
        type = ((GrVariable)element).getTypeGroovy();
      }
      generateForClosureType(buffer, type);
    }
    else if (element instanceof GrClosureType) {
      generateForClosureType(buffer, (GrClosureType)element);
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

  private static void generateForClosureType(StringBuilder buffer, PsiType type) {
    if (type instanceof GrClosureType) {
      GrClosureParameter[] parameters = ((GrClosureType)type).getSignature().getParameters();
      if (parameters.length > 0) {
        for (int i = 0; i < parameters.length; i++) {
          if (i > 0) buffer.append(", ");
          final PsiType psiType = parameters[i].getType();
          if (psiType == null) {
            buffer.append("def");
          }
          else {
            buffer.append(psiType.getPresentableText());
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
  }

  private static void appendParameterText(PsiParameter param, PsiSubstitutor substitutor, StringBuilder buffer) {
    if (param instanceof GrParameter) {
      GrParameter grParam = (GrParameter)param;
      GroovyPresentationUtil.appendParameterPresentation(grParam, substitutor, true, buffer);

      final GrExpression initializer = grParam.getDefaultInitializer();
      if (initializer != null) {
        buffer.append(" = ").append(initializer.getText());
      }
      else if (grParam.isOptional()) {
        buffer.append(" = null");
      }
    } else {
      PsiType t = param.getType();
      PsiType paramType = substitutor.substitute(t);
      buffer.append(paramType.getPresentableText());
      String name = param.getName();
      if (name != null) {
        buffer.append(" ");
        buffer.append(name);
      }
    }
  }

  @NotNull
  @Override
  public GroovyPsiElement[] getActualParameters(@NotNull GroovyPsiElement o) {
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

  private static final Set<Class> ALLOWED_PARAM_CLASSES = Collections.<Class>singleton(GroovyPsiElement.class);

  @NotNull
  @Override
  public Set<Class> getArgumentListAllowedParentClasses() {
    return ALLOWED_PARAM_CLASSES;
  }

  @NotNull
  @Override
  public Class<GroovyPsiElement> getArgumentListClass() {
    return GroovyPsiElement.class;
  }
}
