// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.jvm.DefaultJvmElementVisitor;
import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmElementVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.ComponentNotRegisteredInspection;
import org.jetbrains.idea.devkit.inspections.DevKitJvmInspection;
import org.jetbrains.idea.devkit.inspections.RegistrationCheckerUtil;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterStatisticsCollectorFix;
import org.jetbrains.idea.devkit.util.StatisticsCollectorType;

import java.util.Set;

public class StatisticsCollectorNotRegisteredInspection extends DevKitJvmInspection {
  public static final String FEATURE_USAGES_COLLECTOR = "com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector";

  @Nullable
  @Override
  protected JvmElementVisitor<Boolean> buildVisitor(@NotNull Project project, @NotNull HighlightSink sink, boolean isOnTheFly) {
    return new DefaultJvmElementVisitor<>() {
      @Override
      public Boolean visitClass(@NotNull JvmClass clazz) {
        PsiElement sourceElement = clazz.getSourceElement();
        if (!(sourceElement instanceof PsiClass)) {
          return null;
        }
        checkClass(project, (PsiClass)sourceElement, sink);
        return false;
      }
    };
  }

  private static void checkClass(@NotNull Project project, @NotNull PsiClass checkedClass, @NotNull HighlightSink sink) {
    if (checkedClass.getQualifiedName() == null ||
        checkedClass.getContainingFile().getVirtualFile() == null ||
        checkedClass.hasModifierProperty(PsiModifier.ABSTRACT) ||
        checkedClass.isEnum()) {
      return;
    }

    GlobalSearchScope scope = checkedClass.getResolveScope();
    PsiClass featureUsageCollectorClass = JavaPsiFacade.getInstance(project).findClass(FEATURE_USAGES_COLLECTOR, scope);
    if (featureUsageCollectorClass != null && checkedClass.isInheritor(featureUsageCollectorClass, true)) {
      for (StatisticsCollectorType collectorType : StatisticsCollectorType.values()) {
        if (InheritanceUtil.isInheritor(checkedClass, collectorType.getClassName())) {
          checkCollectorRegistration(checkedClass, sink, collectorType);
          return;
        }
      }
    }
  }

  private static void checkCollectorRegistration(@NotNull PsiClass checkedClass,
                                                 @NotNull HighlightSink sink,
                                                 @NotNull StatisticsCollectorType collectorType) {
    final Set<PsiClass> types = RegistrationCheckerUtil.getRegistrationTypes(checkedClass,
                                                                             RegistrationCheckerUtil.RegistrationType.STATISTICS_COLLECTOR);
    if (types != null && !types.isEmpty()) {
      return;
    }
    if (!ComponentNotRegisteredInspection.canFix(checkedClass)) {
      return;
    }
    LocalQuickFix fix = new RegisterStatisticsCollectorFix(checkedClass, collectorType);
    sink.highlight(DevKitBundle.message("inspections.statistics.collector.not.registered.message"), fix);
  }
}
