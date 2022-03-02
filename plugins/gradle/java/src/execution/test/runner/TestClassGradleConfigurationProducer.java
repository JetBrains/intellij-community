// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.junit.InheritorChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil.createTestFilterFrom;

public class TestClassGradleConfigurationProducer extends AbstractGradleTestRunConfigurationProducer<PsiClass, PsiClass> {
  @Override
  public boolean isPreferredConfiguration(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return !other.isProducedBy(PatternGradleConfigurationProducer.class) &&
           !other.isProducedBy(TestMethodGradleConfigurationProducer.class) &&
           super.isPreferredConfiguration(self, other);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return !other.isProducedBy(PatternGradleConfigurationProducer.class) &&
           !other.isProducedBy(TestMethodGradleConfigurationProducer.class) &&
           super.shouldReplace(self, other);
  }

  protected @Nullable PsiClass getPsiClassForLocation(Location<?> contextLocation) {
    final Location<?> location = JavaExecutionUtil.stepIntoSingleClass(contextLocation);
    if (location == null) return null;

    TestFrameworks testFrameworks = TestFrameworks.getInstance();
    for (Iterator<Location<PsiClass>> iterator = location.getAncestors(PsiClass.class, false); iterator.hasNext(); ) {
      final Location<PsiClass> classLocation = iterator.next();
      if (testFrameworks.isTestClass(classLocation.getPsiElement())) return classLocation.getPsiElement();
    }
    PsiElement element = location.getPsiElement();
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)element).getClasses();
      if (classes.length == 1 && testFrameworks.isTestClass(classes[0])) return classes[0];
    }
    return null;
  }

  @Override
  protected @Nullable PsiClass getElement(@NotNull ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) return null;
    PsiClass psiClass = getPsiClassForLocation(location);
    if (psiClass == null) return null;
    PsiFile psiFile = psiClass.getContainingFile();
    if (psiFile == null) return null;
    VirtualFile source = psiFile.getVirtualFile();
    if (source == null) return null;
    return psiClass;
  }

  @Override
  protected @NotNull String getLocationName(@NotNull ConfigurationContext context, @NotNull PsiClass element) {
    return Objects.requireNonNull(element.getName());
  }

  @Override
  protected @NotNull String suggestConfigurationName(
    @NotNull ConfigurationContext context,
    @NotNull PsiClass element,
    @NotNull List<? extends PsiClass> chosenElements
  ) {
    List<? extends PsiClass> elements = chosenElements.isEmpty() ? List.of(element) : chosenElements;
    return StringUtil.join(elements, aClass -> aClass.getName(), "|");
  }

  @Override
  protected void chooseSourceElements(
    @NotNull ConfigurationContext context,
    @NotNull PsiClass element,
    @NotNull Consumer<List<PsiClass>> onElementsChosen
  ) {
    InheritorChooser.chooseAbstractClassInheritors(context, element, onElementsChosen);
  }

  @Override
  protected @NotNull List<TestTasksToRun> getAllTestsTaskToRun(
    @NotNull ConfigurationContext context,
    @NotNull PsiClass element,
    @NotNull List<? extends PsiClass> chosenElements
  ) {
    Project project = Objects.requireNonNull(context.getProject());
    VirtualFile source = Objects.requireNonNull(element.getContainingFile().getVirtualFile());
    List<? extends PsiClass> elements = chosenElements.isEmpty() ? List.of(element) : chosenElements;
    List<TestTasksToRun> testsTasksToRun = new ArrayList<>();
    for (PsiClass psiClass : elements) {
      String testFilter = createTestFilterFrom(psiClass);
      testsTasksToRun.addAll(ContainerUtil.map(findAllTestsTaskToRun(source, project), it -> new TestTasksToRun(it, testFilter)));
    }
    return testsTasksToRun;
  }
}
