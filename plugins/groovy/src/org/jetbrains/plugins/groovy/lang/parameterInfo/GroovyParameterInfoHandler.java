package org.jetbrains.plugins.groovy.lang.parameterInfo;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.hint.api.*;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class GroovyParameterInfoHandler implements ParameterInfoHandler<GrArgumentList, PsiNamedElement> {
  public boolean couldShowInLookup() {
    return true;
  }

  public Object[] getParametersForLookup(LookupItem item, ParameterInfoContext context) {
    final PsiElement[] elements = LookupManager.getInstance(context.getProject()).getAllElementsForItem(item);

    if (elements != null) {
      List<PsiMethod> methods = new ArrayList<PsiMethod>();
      for (PsiElement element : elements) {
        if (element instanceof PsiMethod) {
          methods.add((PsiMethod) element);
        }
      }
      return methods.toArray(new Object[methods.size()]);
    }

    return null;
  }

  public Object[] getParametersForDocumentation(PsiNamedElement namedElement, ParameterInfoContext context) {
    if (namedElement instanceof PsiMethod) {
      return ((PsiMethod) namedElement).getParameterList().getParameters();
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public GrArgumentList findElementForParameterInfo(CreateParameterInfoContext context) {
    return getArgumentList(context.getEditor().getCaretModel().getOffset(), context.getFile());
  }

  public GrArgumentList findElementForUpdatingParameterInfo(UpdateParameterInfoContext context) {
    return getArgumentList(context.getEditor().getCaretModel().getOffset(), context.getFile());
  }

  private GrArgumentList getArgumentList(int offset, PsiFile file) {
    final PsiElement element = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(element, GrArgumentList.class);
  }

  public void showParameterInfo(@NotNull GrArgumentList list, CreateParameterInfoContext context) {
    final PsiElement parent = list.getParent();
    if (parent instanceof GrCallExpression) {
      final PsiNamedElement[] variants = ((GrCallExpression) parent).getMethodVariants();
      context.setItemsToShow(variants);
      context.showHint(list, list.getTextRange().getStartOffset(), this);
    }
  }

  public void updateParameterInfo(@NotNull GrArgumentList list, UpdateParameterInfoContext context) {
    context.setCurrentParameter(getCurrentParameterIndex(list, context.getEditor().getCaretModel().getOffset()));
    final Object[] objects = context.getObjectsToView();
    for (int i = 0; i < objects.length; i++) {
      Object object = objects[i];
      final PsiNamedElement namedElement = (PsiNamedElement) object;
      if (!namedElement.isValid()) {
        context.setUIComponentEnabled(i, false);
        continue;
      } else {

      }
    }
  }

  private int getCurrentParameterIndex(GrArgumentList list, int offset) {
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

    return -1;
  }


  public String getParameterCloseChars() {
    return ",){}";
  }

  public boolean tracksParameterIndex() {
    return true;
  }

  public void updateUI(PsiNamedElement namedElement, ParameterInfoUIContext context) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();

    if (!namedElement.isValid()) {
      context.setUIComponentEnabled(false);
      return;
    }

    int highlightStartOffset = -1;
    int highlightEndOffset = -1;

    StringBuffer buffer = new StringBuffer();

    if (namedElement instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) namedElement;
      if (settings.SHOW_FULL_SIGNATURES_IN_PARAMETER_INFO) {
        if (!method.isConstructor()) {
          PsiType returnType = method.getReturnType();
          buffer.append(returnType.getPresentableText());
          buffer.append(" ");
        }
        buffer.append(namedElement.getName());
        buffer.append("(");
      }

      final int currentParameter = context.getCurrentParameterIndex();

      PsiParameter[] parms = method.getParameterList().getParameters();
      int numParams = parms.length;
      if (numParams > 0) {
        for (int j = 0; j < numParams; j++) {
          PsiParameter parm = parms[j];

          int startOffset = buffer.length();

          if (parm.isValid()) {
            PsiType paramType = parm.getType();
            buffer.append(paramType.getPresentableText());
            String name = parm.getName();
            if (name != null) {
              buffer.append(" ");
              buffer.append(name);
            }
          }

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

    } else if (namedElement instanceof PsiClass) {
      buffer.append("no parameters");
    }

    final boolean isDeprecated = namedElement instanceof PsiDocCommentOwner && ((PsiDocCommentOwner) namedElement).isDeprecated();

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
}
