package org.jetbrains.plugins.groovy.lang.parameterInfo;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.lang.parameterInfo.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.arithmetic.TypesUtil;
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
    final PsiElement[] elements = LookupManager.getInstance(context.getProject()).getAllElementsForItem((LookupItem) item);

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

  public Object[] getParametersForDocumentation(GroovyResolveResult resolveResult, ParameterInfoContext context) {
    final PsiElement element = resolveResult.getElement();
    if (element instanceof PsiMethod) {
      return ((PsiMethod) element).getParameterList().getParameters();
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public GroovyPsiElement findElementForParameterInfo(CreateParameterInfoContext context) {
    return getArgumentList(context.getEditor().getCaretModel().getOffset(), context.getFile());
  }

  public GroovyPsiElement findElementForUpdatingParameterInfo(UpdateParameterInfoContext context) {
    return getArgumentList(context.getEditor().getCaretModel().getOffset(), context.getFile());
  }

  private GroovyPsiElement getArgumentList(int offset, PsiFile file) {
    final PsiElement element = file.findElementAt(offset);
    return PsiTreeUtil.getParentOfType(element, GrArgumentList.class, GrCommandArgumentList.class);
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
        variants = new GroovyResolveResult[constructors.length];
        for (int i = 0; i < constructors.length; i++) {
          final boolean isAccessible = com.intellij.psi.util.PsiUtil.isAccessible(constructors[i], place, null);
          variants[i] = new GroovyResolveResultImpl(constructors[i], isAccessible);
        }
      }
    } else if (parent instanceof GrApplicationStatement) {
      final GrExpression funExpr = ((GrApplicationStatement) parent).getFunExpression();
      if (funExpr instanceof GrReferenceExpression) {
        variants = ((GrReferenceExpression) funExpr).getSameNameVariants();
      }
    }
    context.setItemsToShow(variants);
    context.showHint(place, place.getTextRange().getStartOffset(), this);
  }

  public void updateParameterInfo(@NotNull GroovyPsiElement place, UpdateParameterInfoContext context) {
    final int currIndex = getCurrentParameterIndex(place, context.getEditor().getCaretModel().getOffset());
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
        final PsiType[] constructorTypes = PsiUtil.getArgumentTypes(place, true);
        final PsiType[] methodTypes = PsiUtil.getArgumentTypes(place, false);
        if (namedElement instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod) namedElement;
          final PsiParameter[] parameters = method.getParameterList().getParameters();
          PsiType[] argTypes = method.isConstructor() ? constructorTypes : methodTypes;
          if (argTypes == null) continue;

          if (parameters.length <= currIndex) {
            context.setUIComponentEnabled(i, false);
            continue;
          } else {
            for (int j = 0; j < currIndex; j++) {
              PsiType argType = argTypes[j];
              final PsiType paramType = substitutor.substitute(parameters[j].getType());
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
  }

  private int getCurrentParameterIndex(GroovyPsiElement place, int offset) {
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
    } else if (place instanceof GrCommandArgumentList) {
      final GrCommandArgumentList list = (GrCommandArgumentList) place;
      final GrCommandArgument[] labeledArguments = list.getLabeledArguments();
      for (GrCommandArgument labeledArgument : labeledArguments) {
        if (labeledArgument.getTextRange().contains(offset)) return 0; //first Map parameter
      }

      int idx = labeledArguments.length > 0 ? 1 : 0;

      final GrExpression[] exprs = list.getArguments();
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
    assert element != null;
    if (!element.isValid()) {
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
          assert returnType != null;
          buffer.append(returnType.getPresentableText());
          buffer.append(" ");
        }
        buffer.append(element.getName());
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
            PsiType paramType = resolveResult.getSubstitutor().substitute(parm.getType());
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

    } else if (element instanceof PsiClass) {
      buffer.append("no parameters");
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
}
