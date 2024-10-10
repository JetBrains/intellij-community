// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.java.library.JavaLibraryModificationTracker;
import com.intellij.java.library.JavaLibraryUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IntelliJProjectUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.ui.components.JBList;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;
import org.jetbrains.uast.*;

import java.io.File;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class PsiUtil {
  private static final @NonNls String IDE_PROJECT_MARKER_CLASS = JBList.class.getName();
  private static final @NonNls String[] IDEA_PROJECT_MARKER_FILES = {
    "idea.iml", "community-main.iml", "intellij.idea.community.main.iml", "intellij.idea.ultimate.main.iml"
  };

  private PsiUtil() { }

  /**
   * @return {@code true} if given class can be instantiated by container at runtime
   */
  public static boolean isInstantiable(@NotNull PsiClass psiClass) {
    if (psiClass instanceof PsiTypeParameter ||
        psiClass.hasModifierProperty(PsiModifier.PRIVATE) ||
        psiClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }

    if (psiClass.getContainingClass() != null &&
        !psiClass.hasModifierProperty(PsiModifier.STATIC)) {
      return false;
    }

    PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) return true;

    // e.g., Project / Project, CoroutineScope
    for (PsiMethod constructor : constructors) {
      int parametersCount = constructor.getParameterList().getParametersCount();
      if (parametersCount <= 2) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  public static PsiMethod findNearestMethod(String name, @Nullable PsiClass cls) {
    if (cls == null) return null;
    for (PsiMethod method : cls.findMethodsByName(name, false)) {
      if (method.getParameterList().isEmpty()) {
        return method.getModifierList().hasModifierProperty(PsiModifier.ABSTRACT) ? null : method;
      }
    }
    return findNearestMethod(name, cls.getSuperClass());
  }

  @Nullable
  public static PsiAnnotation findAnnotation(final Class<?> annotationClass, PsiMember... members) {
    for (PsiMember member : members) {
      if (member == null) continue;

      final PsiAnnotation annotation = member.getAnnotation(annotationClass.getName());
      if (annotation != null) return annotation;
    }
    return null;
  }

  @Nullable
  public static String getAnnotationStringAttribute(final PsiAnnotation annotation,
                                                    final String name,
                                                    String defaultValueIfEmpty) {
    final String value = AnnotationUtil.getDeclaredStringAttributeValue(annotation, name);
    return StringUtil.defaultIfEmpty(value, defaultValueIfEmpty);
  }

  public static boolean getAnnotationBooleanAttribute(final PsiAnnotation annotation,
                                                      final String name) {
    return ObjectUtils.notNull(AnnotationUtil.getBooleanAttributeValue(annotation, name), Boolean.FALSE);
  }

  /**
   * @deprecated Use {@linkplain IntelliJProjectUtil#isIntelliJPlatformProject(Project)} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @ApiStatus.Internal
  @Deprecated
  public static boolean isIdeaProject(@Nullable Project project) {
    return IntelliJProjectUtil.isIntelliJPlatformProject(project);
  }

  /**
   * @deprecated Use {@linkplain IntelliJProjectUtil#markAsIntelliJPlatformProject(Project, Boolean)} instead
   */
  @TestOnly
  @Deprecated(forRemoval = true)
  public static void markAsIdeaProject(@NotNull Project project, boolean value) {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, value);
  }

  @Nullable
  public static UExpression getReturnedExpression(PsiMethod method) {
    UMethod uMethod = UastContextKt.toUElement(method, UMethod.class);
    if (uMethod == null) return null;

    final UExpression uBody = uMethod.getUastBody();
    if (!(uBody instanceof UBlockExpression)) return null;
    final List<UExpression> expressions = ((UBlockExpression)uBody).getExpressions();
    final UExpression singleExpression = ContainerUtil.getOnlyItem(expressions);
    if (singleExpression == null) return null;

    if (!(singleExpression instanceof UReturnExpression uReturnExpression)) return null;
    final UExpression returnValue = uReturnExpression.getReturnExpression();
    if (returnValue == null) return null;

    if (returnValue instanceof UReferenceExpression referenceExpression) {
      final UElement uElement = UResolvableKt.resolveToUElement(referenceExpression);
      final UField uField = ObjectUtils.tryCast(uElement, UField.class);
      if (uField != null && uField.isFinal()) {
        return uField.getUastInitializer();
      }

      return ObjectUtils.tryCast(uElement, UExpression.class);
    }
    else if (returnValue instanceof UCallExpression uCallExpression) {
      final PsiMethod psiMethod = uCallExpression.resolve();
      if (psiMethod == null) return null;
      return getReturnedExpression(psiMethod);
    }

    return returnValue;
  }

  @RequiresReadLock
  public static boolean isPluginProject(@NotNull final Project project) {
    return JavaLibraryUtil.hasLibraryClass(project, IDE_PROJECT_MARKER_CLASS);
  }

  public static boolean isPluginModule(@NotNull final Module module) {
    return CachedValuesManager.getManager(module.getProject()).getCachedValue(module, () -> {
      boolean foundMarkerClass = JavaPsiFacade.getInstance(module.getProject())
                                   .findClass(IDE_PROJECT_MARKER_CLASS,
                                              GlobalSearchScope.moduleRuntimeScope(module, false)) != null; // must use runTimeScope
      return Result.createSingleDependency(foundMarkerClass, JavaLibraryModificationTracker.getInstance(module.getProject()));
    });
  }

  public static boolean isPluginXmlPsiElement(@NotNull PsiElement element) {
    return isPluginProject(element.getProject()) && DescriptorUtil.isPluginXml(element.getContainingFile());
  }

  @ApiStatus.Internal
  public static boolean isPathToIntelliJIdeaSources(String path) {
    for (String file : IDEA_PROJECT_MARKER_FILES) {
      if (new File(path, file).isFile()) return true;
    }
    return false;
  }
}
