// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.lang.jvm.DefaultJvmElementVisitor;
import com.intellij.lang.jvm.JvmClass;
import com.intellij.lang.jvm.JvmElementVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.inspections.DevKitJvmInspection;
import org.jetbrains.idea.devkit.inspections.ExtensionUtil;
import org.jetbrains.idea.devkit.inspections.quickfix.RegisterExtensionFix;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.ExtensionPointLocator;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jetbrains.idea.devkit.util.ExtensionLocatorKt.processExtensionDeclarations;

@VisibleForTesting
@ApiStatus.Internal
public final class StatisticsCollectorNotRegisteredInspection extends DevKitJvmInspection {
  private static final String FEATURE_USAGES_COLLECTOR = "com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector";

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
    if (!ExtensionUtil.isExtensionPointImplementationCandidate(checkedClass)) {
      return;
    }

    if (InheritanceUtil.isInheritor(checkedClass, FEATURE_USAGES_COLLECTOR)) {
      for (StatisticsCollectorType collectorType : StatisticsCollectorType.values()) {
        if (InheritanceUtil.isInheritor(checkedClass, collectorType.getClassName())) {
          checkCollectorRegistration(project, checkedClass, sink, collectorType);
          return;
        }
      }
    }
  }

  private static void checkCollectorRegistration(@NotNull Project project,
                                                 @NotNull PsiClass checkedClass,
                                                 @NotNull HighlightSink sink,
                                                 @NotNull StatisticsCollectorType collectorType) {
    String qualifiedName = ClassUtil.getJVMClassName(checkedClass);
    if (qualifiedName == null) {
      return;
    }
    AtomicBoolean isCollectorRegistered = new AtomicBoolean(false);
    processExtensionDeclarations(getNameToSearch(checkedClass, qualifiedName), project, false, (extension, tag) -> {
      ExtensionPoint point = extension.getExtensionPoint();
      if (point != null && point.getEffectiveQualifiedName().equals(collectorType.getExtensionPoint()) &&
          ContainerUtil.exists(tag.getAttributes(), it -> Objects.equals(it.getValue(), qualifiedName))) {
        isCollectorRegistered.set(true);
        return false;
      }
      return true;
    });

    if (isCollectorRegistered.get()) {
      return;
    }

    ExtensionPointLocator extensionPointLocator = new ExtensionPointLocator(checkedClass);
    Set<ExtensionPointCandidate> candidateList = extensionPointLocator.findSuperCandidates();
    if (!candidateList.isEmpty()) {
      RegisterExtensionFix fix = new RegisterExtensionFix(checkedClass, candidateList);
      sink.highlight(DevKitBundle.message("inspections.statistics.collector.not.registered.message"), fix);
    }
  }

  private static @NotNull String getNameToSearch(@NotNull PsiClass checkedClass, String qualifiedName) {
    PsiClass parentClass = ObjectUtils.tryCast(checkedClass.getParent(), PsiClass.class);
    if (parentClass == null) {
      return qualifiedName;
    }
    else {
      // package.parent$inner string cannot be found, so, search by parent FQN
      String parentQualifiedName = parentClass.getQualifiedName();
      return parentQualifiedName != null ? parentQualifiedName : qualifiedName;
    }
  }

  private enum StatisticsCollectorType {
    COUNTER("com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector",
            "com.intellij.statistics.counterUsagesCollector"),
    PROJECT("com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector",
            "com.intellij.statistics.projectUsagesCollector"),
    APPLICATION("com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector",
                "com.intellij.statistics.applicationUsagesCollector");

    private final String myClassName;
    private final String myExtensionPoint;

    StatisticsCollectorType(String className, String extensionPoint) {
      this.myClassName = className;
      this.myExtensionPoint = extensionPoint;
    }

    public String getClassName() {
      return myClassName;
    }

    public String getExtensionPoint() {
      return myExtensionPoint;
    }
  }
}
