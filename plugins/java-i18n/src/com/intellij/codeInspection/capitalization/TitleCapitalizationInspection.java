/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.capitalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.*;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author yole
 */
public class TitleCapitalizationInspection extends BaseJavaLocalInspectionTool {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return "Plugin DevKit";
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Incorrect dialog title capitalization";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "DialogTitleCapitalization";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        PsiType type = method.getReturnType();
        if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_STRING)) return;
        Collection<PsiReturnStatement> statements = PsiTreeUtil.findChildrenOfType(method, PsiReturnStatement.class);
        for (PsiReturnStatement returnStatement : statements) {
          PsiExpression expression = returnStatement.getReturnValue();
          String value = getTitleValue(expression);
          if (value == null) continue;
          Nls.Capitalization capitalization = getCapitalizationFromAnno(method);
          checkCapitalization(expression, holder, capitalization);
        }
      }

      @Override
      public void visitCallExpression(PsiCallExpression expression) {
        PsiMethod psiMethod = expression.resolveMethod();
        if (psiMethod != null) {
          PsiExpressionList argumentList = expression.getArgumentList();
          if (argumentList != null) {
            PsiExpression[] args = argumentList.getExpressions();
            PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
            for (int i = 0; i < Math.min(parameters.length, args.length); i++) {
              PsiParameter parameter = parameters[i];
              Nls.Capitalization capitalization = getCapitalizationFromAnno(parameter);
              checkCapitalization(args[i], holder, capitalization);
            }
          }
        }
      }
    };
  }

  public Nls.Capitalization getCapitalizationFromAnno(PsiModifierListOwner modifierListOwner) {
    PsiAnnotation nls = AnnotationUtil.findAnnotationInHierarchy(modifierListOwner, Collections.singleton(Nls.class.getName()));
    if (nls == null) return Nls.Capitalization.NotSpecified;
    PsiAnnotationMemberValue capitalization = nls.findAttributeValue("capitalization");
    Object cap = JavaPsiFacade.getInstance(modifierListOwner.getProject()).getConstantEvaluationHelper().computeConstantExpression(capitalization);
    return cap instanceof Nls.Capitalization ? (Nls.Capitalization)cap : Nls.Capitalization.NotSpecified;
  }

  private static void checkCapitalization(PsiExpression element, @NotNull ProblemsHolder holder, Nls.Capitalization capitalization) {
    if (capitalization == Nls.Capitalization.NotSpecified) return;
    String titleValue = getTitleValue(element);
    if (!checkCapitalization(titleValue, capitalization)) {
      holder.registerProblem(element, "String '" + titleValue + "' is not properly capitalized. It should have " +
                                      StringUtil.toLowerCase(capitalization.toString()) + " capitalization",
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new TitleCapitalizationFix(titleValue, capitalization));
    }
  }

  @Nullable
  private static String getTitleValue(@Nullable PsiExpression arg) {
    if (arg instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)arg).getValue();
      if (value instanceof String) {
        return (String) value;
      }
    }
    if (arg instanceof PsiMethodCallExpression) {
      PsiMethod psiMethod = ((PsiMethodCallExpression)arg).resolveMethod();
      PsiExpression returnValue = PropertyUtil.getGetterReturnExpression(psiMethod);
      if (arg == returnValue) {
        return null;
      }
      if (returnValue != null) {
        return getTitleValue(returnValue);
      }
      Property propertyArgument = getPropertyArgument((PsiMethodCallExpression)arg);
      if (propertyArgument != null) {
        return propertyArgument.getUnescapedValue();
      }
    }
    if (arg instanceof PsiReferenceExpression) {
      PsiElement result = ((PsiReferenceExpression)arg).resolve();
      if (result instanceof PsiVariable && ((PsiVariable)result).hasModifierProperty(PsiModifier.FINAL)) {
        return getTitleValue(((PsiVariable) result).getInitializer());
      }
    }
    return null;
  }

  @Nullable
  private static Property getPropertyArgument(PsiMethodCallExpression arg) {
    PsiExpression[] args = arg.getArgumentList().getExpressions();
    if (args.length > 0) {
      PsiReference[] references = args[0].getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof PropertyReference) {
          ResolveResult[] resolveResults = ((PropertyReference)reference).multiResolve(false);
          if (resolveResults.length == 1 && resolveResults[0].isValidResult()) {
            PsiElement element = resolveResults[0].getElement();
            if (element instanceof Property) {
              return (Property) element;
            }
          }
        }
      }
    }
    return null;
  }

  public static boolean checkCapitalization(String value, Nls.Capitalization capitalization) {
    if (StringUtil.isEmpty(value) || capitalization == Nls.Capitalization.NotSpecified) {
      return true;
    }
    value = value.replace("&", "");
    return capitalization == Nls.Capitalization.Title
           ? StringUtil.wordsToBeginFromUpperCase(value).equals(value)
           : checkSentenceCapitalization(value);
  }

  private static boolean checkSentenceCapitalization(@NotNull String value) {
    String[] words = value.split(" ");
    if (words.length == 0) return true;
    if (!isCapitalizedWord(words[0])) return false;
    for (int i = 1; i < words.length; i++) {
      String word = words[i];
      if (isCapitalizedWord(word)) {
        // check for abbreviations like SQL or I18n
        if (word.length() == 1 || !Character.isLowerCase(word.charAt(1)))
          continue;
        return false;
      }
    }
    return true;
  }

  private static boolean isCapitalizedWord(@Nullable String word) {
    return StringUtil.isNotEmpty(word) && (!Character.isLetter(word.charAt(0)) || StringUtil.isCapitalized(word));
  }

  private static class TitleCapitalizationFix implements LocalQuickFix {

    private final String myTitleValue;
    private final Nls.Capitalization myCapitalization;

    public TitleCapitalizationFix(String titleValue, Nls.Capitalization capitalization) {
      myTitleValue = titleValue;
      myCapitalization = capitalization;
    }

    @NotNull
    @Override
    public String getName() {
      return "Properly capitalize '" + myTitleValue + '\'';
    }

    public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement problemElement = descriptor.getPsiElement();
      if (problemElement == null || !problemElement.isValid()) {
        return;
      }
      if (isQuickFixOnReadOnlyFile(problemElement)) {
        return;
      }
      try {
        doFix(project, problemElement);
      }
      catch (IncorrectOperationException e) {
        final Class<? extends TitleCapitalizationFix> aClass = getClass();
        final String className = aClass.getName();
        final Logger logger = Logger.getInstance(className);
        logger.error(e);
      }
    }

    protected void doFix(Project project, PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
        final Object value = literalExpression.getValue();
        if (!(value instanceof String)) {
          return;
        }
        final String string = (String)value;
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final PsiExpression newExpression = factory.createExpressionFromText('"' + fixValue(string) + '"', element);
        literalExpression.replace(newExpression);
      }
      else if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        final PsiMethod method = methodCallExpression.resolveMethod();
        final PsiExpression returnValue = PropertyUtil.getGetterReturnExpression(method);
        if (returnValue != null) {
          doFix(project, returnValue);
        }
        final Property property = getPropertyArgument(methodCallExpression);
        if (property == null) {
          return;
        }
        final String value = property.getUnescapedValue();
        if (value == null) {
          return;
        }
        property.setValue(fixValue(value));
      }
      else if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        final PsiVariable variable = (PsiVariable)target;
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            doFix(project, variable.getInitializer());
          }
      }
    }

    @NotNull
    private String fixValue(String string) {
      return myCapitalization == Nls.Capitalization.Title
             ? StringUtil.wordsToBeginFromUpperCase(string)
             : StringUtil.capitalize(StringUtil.wordsToBeginFromLowerCase(string));
    }

    protected static boolean isQuickFixOnReadOnlyFile(PsiElement problemElement) {
      final PsiFile containingPsiFile = problemElement.getContainingFile();
      if (containingPsiFile == null) {
        return false;
      }
      final VirtualFile virtualFile = containingPsiFile.getVirtualFile();
      if (virtualFile == null) {
        return false;
      }
      final Project project = problemElement.getProject();
      final ReadonlyStatusHandler handler = ReadonlyStatusHandler.getInstance(project);
      final ReadonlyStatusHandler.OperationStatus status = handler.ensureFilesWritable(virtualFile);
      return status.hasReadonlyFiles();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Properly capitalize";
    }
  }
}
