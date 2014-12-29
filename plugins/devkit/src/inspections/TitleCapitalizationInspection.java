/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.*;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
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
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        String calledName = methodExpression.getReferenceName();
        if (calledName == null) return;
        if ("setTitle".equals(calledName)) {
          if (!isMethodOfClass(expression, DialogWrapper.class.getName(), FileChooserDescriptor.class.getName())) return;
          PsiExpression[] args = expression.getArgumentList().getExpressions();
          if (args.length == 0) {
            return;
          }
          checkCapitalization(args[0], holder, Nls.Capitalization.Title);
        }
        else if (calledName.startsWith("show") && (calledName.endsWith("Dialog") || calledName.endsWith("Message"))) {
          if (!isMethodOfClass(expression, Messages.class.getName())) return;
          PsiExpression[] args = expression.getArgumentList().getExpressions();
          PsiMethod psiMethod = expression.resolveMethod();
          assert psiMethod != null;
          PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
          for (int i = 0, parametersLength = parameters.length; i < parametersLength; i++) {
            PsiParameter parameter = parameters[i];
            if ("title".equals(parameter.getName()) && i < args.length) {
              checkCapitalization(args[i], holder, Nls.Capitalization.Title);
              break;
            }
          }
        }
      }
    };
  }

  public Nls.Capitalization getCapitalizationFromAnno(PsiMethod method) {
    PsiAnnotation nls = AnnotationUtil.findAnnotationInHierarchy(method, Collections.singleton(Nls.class.getName()));
    if (nls == null) return Nls.Capitalization.NotSpecified;
    PsiAnnotationMemberValue capitalization = nls.findAttributeValue("capitalization");
    Object cap = JavaPsiFacade.getInstance(method.getProject()).getConstantEvaluationHelper().computeConstantExpression(capitalization);
    return cap instanceof Nls.Capitalization ? (Nls.Capitalization)cap : Nls.Capitalization.NotSpecified;
  }

  private static void checkCapitalization(PsiExpression element, @NotNull ProblemsHolder holder, Nls.Capitalization capitalization) {
    String titleValue = getTitleValue(element);
    if (!checkCapitalization(titleValue, capitalization)) {
      holder.registerProblem(element, "String '" + titleValue + "' is not properly capitalized. It should have " +
                                      StringUtil.toLowerCase(capitalization.toString()) + " capitalization",
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new TitleCapitalizationFix(titleValue, capitalization));
    }
  }

  private static boolean isMethodOfClass(PsiMethodCallExpression expression, String... classNames) {
    PsiMethod psiMethod = expression.resolveMethod();
    if (psiMethod == null) {
      return false;
    }
    PsiClass containingClass = psiMethod.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    String name = containingClass.getQualifiedName();
    return ArrayUtil.contains(name, classNames);
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

  private static boolean checkCapitalization(String value, Nls.Capitalization capitalization) {
    if (value == null || capitalization == Nls.Capitalization.NotSpecified) {
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
    if (!StringUtil.isCapitalized(words[0])) return false;
    for (int i = 1; i < words.length; i++) {
      String word = words[i];
      if (StringUtil.isCapitalized(word)) return false;
    }
    return true;
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
