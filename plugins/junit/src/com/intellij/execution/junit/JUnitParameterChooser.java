// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.MetaAnnotationUtil;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.junit.JUnitParameterCollector.Parameter;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.intellij.execution.junit.JUnitUtil.TEST5_FACTORY_ANNOTATION;
import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_REPEATED_TEST;
import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_TEST_TEMPLATE;
import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_CLASS;

/**
 * Asks which parameter of a parameterized test to run, and hands the choice over to {@link JUnitSingleParameter}.
 * The choice is offered on every launch from the context, the same way the inheritor of an abstract test class is chosen,
 * see {@link InheritorChooser}.
 */
final class JUnitParameterChooser {
  private final @NotNull RunnerAndConfigurationSettings mySettings;
  private final @NotNull @NlsSafe String myTestName;
  private final @NotNull DataContext myDataContext;
  private final @NotNull Runnable myRunConfiguration;

  private JUnitParameterChooser(@NotNull RunnerAndConfigurationSettings settings,
                                @NotNull @NlsSafe String testName,
                                @NotNull DataContext dataContext,
                                @NotNull Runnable runConfiguration) {
    mySettings = settings;
    myTestName = testName;
    myDataContext = dataContext;
    myRunConfiguration = () -> {
      try (AccessToken ignore = SlowOperations.startSection(SlowOperations.ACTION_PERFORM)) {
        WriteIntentReadAction.run(runConfiguration);
      }
    };
  }

  static @Nullable JUnitParameterChooser of(@NotNull ConfigurationFromContext configuration,
                                            @NotNull ConfigurationContext context,
                                            @NotNull Runnable runConfiguration) {
    if (!(configuration.getConfiguration() instanceof JUnitConfiguration junitConfiguration)) return null;
    if (!(configuration.getSourceElement() instanceof PsiMember test)) return null;
    if (!isOffered(junitConfiguration, test)) return null;

    @NlsSafe String testName = ReadAction.computeBlocking(test::getName);
    if (testName == null) return null;
    return new JUnitParameterChooser(configuration.getConfigurationSettings(), testName, context.getDataContext(), runConfiguration);
  }

  /** Whether the parameters of {@code test} can be chosen before it runs. */
  static boolean isOffered(@NotNull JUnitConfiguration configuration, @Nullable PsiElement test) {
    return RegistryManager.getInstance().is("junit.choose.test.parameter") &&
           // the fork that collects the parameters is started locally
           !configuration.needPrepareTarget() &&
           isParameterized(test);
  }

  private static boolean isParameterized(@Nullable PsiElement test) {
    return ReadAction.computeBlocking(() -> {
      if (test instanceof PsiMethod method) {
        return method.isValid() &&
               MetaAnnotationUtil.isMetaAnnotated(method, Set.of(ORG_JUNIT_JUPITER_API_TEST_TEMPLATE, TEST5_FACTORY_ANNOTATION)) &&
               !MetaAnnotationUtil.isMetaAnnotated(method, Set.of(ORG_JUNIT_JUPITER_API_REPEATED_TEST));
      }
      if (test instanceof PsiClass testClass) {
        return testClass.isValid() &&
               MetaAnnotationUtil.isMetaAnnotatedInHierarchy(testClass, Set.of(ORG_JUNIT_JUPITER_PARAMS_PARAMETERIZED_CLASS));
      }
      return false;
    });
  }

  void chooseAndRun() {
    show(JBPopupFactory.getInstance()
           .createConfirmation(JUnitBundle.message("choose.parameter.title", myTestName),
                               JUnitBundle.message("choose.parameter.run.all"),
                               JUnitBundle.message("choose.parameter.choose"),
                               myRunConfiguration,
                               () -> new JUnitParameterCollector(mySettings, myTestName).collect(new JUnitParameterCollector.Callback() {
                                 @Override
                                 public void onCollected(@NotNull List<Parameter> parameters) {
                                   choose(parameters);
                                 }

                                 @Override
                                 public void onCancelled() {
                                   // the user cancelled the build or the collection, so nothing may be launched
                                 }
                               }),
                               0));
  }

  private void choose(@NotNull List<Parameter> parameters) {
    if (parameters.isEmpty()) {
      myRunConfiguration.run(); // nothing to choose from: run the test as it was produced from the context
      return;
    }
    Parameter all = new Parameter(List.of(), JUnitBundle.message("choose.parameter.all", parameters.size()));
    show(JBPopupFactory.getInstance()
           .createPopupChooserBuilder(ContainerUtil.prepend(parameters, all))
           .setRenderer(BuilderKt.textListCellRenderer(Parameter::displayName))
           .setTitle(JUnitBundle.message("choose.parameter.list.title", myTestName))
           .setNamerForFiltering(Parameter::displayName)
           .setAutoselectOnMouseMove(false)
           .setMovable(true)
           .setResizable(false)
           .setRequestFocus(true)
           .setMinSize(JBUI.size(270, 55))
           // switching to setItemsChosenCallback is all it takes to let several parameters be run at once
           .setItemChosenCallback(chosen -> {
             if (chosen != all) new JUnitSingleParameter(mySettings).updateConfiguration(chosen);
             myRunConfiguration.run();
           })
           .createPopup());
  }

  private void show(@NotNull JBPopup popup) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (popup.isDisposed()) return;
      if (CommonDataKeys.EDITOR.getData(myDataContext) != null ||
          PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(myDataContext) != null) {
        popup.showInBestPositionFor(myDataContext);
      }
      else {
        // showInBestPositionFor asserts when there is nothing to anchor to, and a launch may come from a place with no component
        popup.showInFocusCenter();
      }
      // ModalityState.any(): the popup has to appear even while a modal progress of the launch is up
    }, ModalityState.any());
  }
}
