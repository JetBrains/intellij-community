/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.util;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.ui.components.JBList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @author Konstantin Bulenkov
 */
public class PsiUtil {
  private static final Key<Boolean> IDEA_PROJECT = Key.create("idea.internal.inspections.enabled");
  private static final String IDE_PROJECT_MARKER_CLASS = JBList.class.getName();

  private PsiUtil() { }

  public static boolean isInstantiable(@NotNull PsiClass cls) {
    PsiModifierList modList = cls.getModifierList();
    if (modList == null || cls.isInterface() || modList.hasModifierProperty(PsiModifier.ABSTRACT) || !isPublicOrStaticInnerClass(cls)) {
      return false;
    }

    PsiMethod[] constructors = cls.getConstructors();
    if (constructors.length == 0) return true;

    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().getParameters().length == 0
          && constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isPublicOrStaticInnerClass(@NotNull PsiClass cls) {
    PsiModifierList modifiers = cls.getModifierList();
    if (modifiers == null) return false;

    return modifiers.hasModifierProperty(PsiModifier.PUBLIC) &&
           (cls.getParent() instanceof PsiFile || modifiers.hasModifierProperty(PsiModifier.STATIC));
  }

  @Nullable
  public static String getReturnedLiteral(PsiMethod method, PsiClass cls) {
    PsiExpression value = getReturnedExpression(method);
    if (value instanceof PsiLiteralExpression) {
      Object str = ((PsiLiteralExpression)value).getValue();
      return str == null ? null : str.toString();
    }
    else if (value instanceof PsiMethodCallExpression) {
      if (isSimpleClassNameExpression((PsiMethodCallExpression)value)) {
        return cls.getName();
      }
    }
    return null;
  }

  private static boolean isSimpleClassNameExpression(PsiMethodCallExpression expr) {
    String text = expr.getText();
    if (text == null) return false;
    text = text.replaceAll(" ", "")
      .replaceAll("\n", "")
      .replaceAll("\t", "")
      .replaceAll("\r", "");
    return "getClass().getSimpleName()".equals(text) || "this.getClass().getSimpleName()".equals(text);
  }

  @Nullable
  public static PsiExpression getReturnedExpression(PsiMethod method) {
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) {
        PsiExpression value = ((PsiReturnStatement)statements[0]).getReturnValue();
        if (value instanceof PsiReferenceExpression) {
          PsiElement element = ((PsiReferenceExpression)value).resolve();
          if (element instanceof PsiField) {
            PsiField field = (PsiField)element;
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
              return field.getInitializer();
            }
          }
        }
        return value;
      }
    }

    return null;
  }

  @Nullable
  public static PsiMethod findNearestMethod(String name, @Nullable PsiClass cls) {
    if (cls == null) return null;
    for (PsiMethod method : cls.getMethods()) {
      if (method.getParameterList().getParametersCount() == 0 && method.getName().equals(name)) {
        return method.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT) ? null : method;
      }
    }
    return findNearestMethod(name, cls.getSuperClass());
  }


  public static boolean isIdeaProject(@Nullable Project project) {
    if (project == null) return false;

    Boolean flag = project.getUserData(IDEA_PROJECT);
    if (flag == null) {
      flag = checkIdeaProject(project);
      project.putUserData(IDEA_PROJECT, flag);
    }

    return flag;
  }

  public static boolean isPluginProject(@NotNull final Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> {
      boolean foundMarkerClass =
        JavaPsiFacade.getInstance(project).findClass(IDE_PROJECT_MARKER_CLASS,
                                                     GlobalSearchScope.allScope(project)) != null;
      return CachedValueProvider.Result.createSingleDependency(foundMarkerClass, ProjectRootManager.getInstance(project));
    });
  }

  public static boolean isPluginModule(@NotNull final Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, () -> {
      boolean foundMarkerClass = JavaPsiFacade.getInstance(module.getProject())
                                   .findClass(IDE_PROJECT_MARKER_CLASS,
                                              GlobalSearchScope.moduleRuntimeScope(module, false)) != null;
      return CachedValueProvider.Result.createSingleDependency(foundMarkerClass, ProjectRootManager.getInstance(module.getProject()));
    });
  }

  private static boolean isIntelliJBasedDir(VirtualFile baseDir) {
    if (baseDir == null) {
      return false;
    }

    for (VirtualFile dir : new VirtualFile[]{baseDir, baseDir.findChild("community"), baseDir.findChild("ultimate")}) {
      if (dir == null || !dir.isDirectory()) {
        continue;
      }
      if (dir.findChild("idea.iml") != null || dir.findChild("community-main.iml") != null) {
        return true;
      }
    }

    return false;
  }

  @TestOnly
  public static void markAsIdeaProject(@NotNull Project project, boolean value) {
    project.putUserData(IDEA_PROJECT, value);
  }

  private static boolean checkIdeaProject(@NotNull Project project) {
    if (!isIntelliJBasedDir(project.getBaseDir())) {
      return false;
    }

    GlobalSearchScope scope = GlobalSearchScopesCore.projectProductionScope(project);
    if (JavaPsiFacade.getInstance(project).findClass(IDE_PROJECT_MARKER_CLASS, scope) == null) {
      return false;
    }

    return true;
  }

  @NotNull
  public static <E extends PsiElement> SmartPsiElementPointer<E> createPointer(@NotNull E e) {
    return SmartPointerManager.getInstance(e.getProject()).createSmartPsiElementPointer(e);
  }
}
