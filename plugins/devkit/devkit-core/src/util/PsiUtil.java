// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.util;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.ui.components.JBList;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;

/**
 * @author Konstantin Bulenkov
 */
public class PsiUtil {
  private static final Key<Boolean> IDEA_PROJECT = Key.create("idea.internal.inspections.enabled");
  private static final String IDE_PROJECT_MARKER_CLASS = JBList.class.getName();
  private static final String[] IDEA_PROJECT_MARKER_FILES = {
    "idea.iml", "community-main.iml", "intellij.idea.community.main.iml", "intellij.idea.ultimate.main.iml"
  };

  private PsiUtil() { }

  public static boolean isInstantiable(@NotNull PsiClass cls) {
    PsiModifierList modList = cls.getModifierList();
    if (modList == null || cls.isInterface() || modList.hasModifierProperty(PsiModifier.ABSTRACT) || !isPublicOrStaticInnerClass(cls)) {
      return false;
    }

    PsiMethod[] constructors = cls.getConstructors();
    if (constructors.length == 0) return true;

    for (PsiMethod constructor : constructors) {
      if (constructor.getParameterList().isEmpty()
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
           (cls.getContainingClass() == null || modifiers.hasModifierProperty(PsiModifier.STATIC));
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
      for (String fileName : IDEA_PROJECT_MARKER_FILES) {
        if (dir.findChild(fileName) != null) {
          return true;
        }
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

    return DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> {
      GlobalSearchScope scope = GlobalSearchScopesCore.projectProductionScope(project);
      return JavaPsiFacade.getInstance(project).findClass(IDE_PROJECT_MARKER_CLASS, scope) != null;
    });
  }

  public static boolean isPluginXmlPsiElement(@NotNull PsiElement element) {
    return isPluginProject(element.getProject()) && DescriptorUtil.isPluginXml(element.getContainingFile());
  }

  public static boolean isPathToIntelliJIdeaSources(String path) {
    for (String file : IDEA_PROJECT_MARKER_FILES) {
      if (new File(path, file).isFile()) return true;
    }
    return false;
  }
}
