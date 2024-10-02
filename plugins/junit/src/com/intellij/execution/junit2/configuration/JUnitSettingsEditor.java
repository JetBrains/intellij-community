// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit2.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.application.JavaSettingsEditorBase;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.execution.ui.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.rt.execution.junit.RepeatCount;
import com.intellij.util.concurrency.NonUrgentExecutor;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static com.intellij.execution.junit.JUnitConfiguration.FORK_NONE;

public class JUnitSettingsEditor extends JavaSettingsEditorBase<JUnitConfiguration> {

  public JUnitSettingsEditor(JUnitConfiguration runConfiguration) {
    super(runConfiguration);
  }

  @Override
  protected void customizeFragments(List<SettingsEditorFragment<JUnitConfiguration, ?>> fragments,
                                    SettingsEditorFragment<JUnitConfiguration, ModuleClasspathCombo> moduleClasspath,
                                    CommonParameterFragments<JUnitConfiguration> commonParameterFragments) {
    DefaultJreSelector jreSelector = DefaultJreSelector.fromModuleDependencies(moduleClasspath.component(), false);
    SettingsEditorFragment<JUnitConfiguration, JrePathEditor> jrePath = CommonJavaFragments.createJrePath(jreSelector);
    fragments.add(jrePath);
    fragments.add(createShortenClasspath(moduleClasspath.component(), jrePath, false));
    if (!getProject().isDefault()) {
      SettingsEditorFragment<JUnitConfiguration, TagButton> fragment =
        SettingsEditorFragment.createTag("test.use.module.path",
                                         ExecutionBundle.message("do.not.use.module.path.tag"),
                                         ExecutionBundle.message("group.java.options"),
                                         configuration -> !configuration.isUseModulePath(),
                                         (configuration, value) -> configuration.setUseModulePath(!value));
      fragments.add(fragment);
      ReadAction.nonBlocking(() -> fragment.setRemovable(
        FilenameIndex.getFilesByName(getProject(), PsiJavaModule.MODULE_INFO_FILE, GlobalSearchScope.projectScope(getProject())).length > 0))
        .expireWith(fragment).submit(NonUrgentExecutor.getInstance());
    }

    ConfigurationModuleSelector moduleSelector = new ConfigurationModuleSelector(getProject(), moduleClasspath.component());
    JUnitTestKindFragment testKind = new JUnitTestKindFragment(getProject(), moduleSelector);
    fragments.add(testKind);

    String group = JUnitBundle.message("test.group");
    VariantTagFragment<JUnitConfiguration, TestSearchScope> scopeFragment =
      VariantTagFragment.createFragment("testScope", JUnitBundle.message("search.scope.name"), group,
                                        () -> new TestSearchScope[]{TestSearchScope.WHOLE_PROJECT, TestSearchScope.SINGLE_MODULE,
                                          TestSearchScope.MODULE_WITH_DEPENDENCIES},
                                        configuration -> configuration.getTestSearchScope(),
                                        (configuration, scope) -> configuration.setSearchScope(scope),
                                        configuration -> configuration.getTestSearchScope() != TestSearchScope.WHOLE_PROJECT);
    scopeFragment.setVariantNameProvider(scope -> scope == TestSearchScope.WHOLE_PROJECT
                                                  ? JUnitBundle.message("search.scope.project")
                                                  : scope == TestSearchScope.SINGLE_MODULE
                                                    ? JUnitBundle.message("search.scope.module")
                                                    : JUnitBundle.message("search.scope.module.deps"));
    scopeFragment.addSettingsEditorListener(editor -> {

      boolean disableModuleClasspath = testKind.disableModuleClasspath(scopeFragment.getSelectedVariant() == TestSearchScope.WHOLE_PROJECT);
      moduleClasspath.setSelected(!disableModuleClasspath && moduleClasspath.isInitiallyVisible(mySettings));
      if (disableModuleClasspath) {
        moduleClasspath.component().setSelectedModule(null);
      }
    });
    fragments.add(scopeFragment);

    VariantTagFragment<JUnitConfiguration, String> repeat =
      VariantTagFragment.createFragment("repeat", JUnitBundle.message("repeat.name"), group,
                                        () -> RepeatCount.REPEAT_TYPES,
                                        configuration -> configuration.getRepeatMode(),
                                        (configuration, mode) -> configuration.setRepeatMode(mode),
                                        configuration -> !RepeatCount.ONCE.equals(configuration.getRepeatMode()));
    repeat.setVariantNameProvider(s -> JUnitBundle.message("junit.configuration.repeat.mode." + s.replace(' ', '.').toLowerCase(Locale.ENGLISH)));
    fragments.add(repeat);

    LabeledComponent<JTextField> countField =
      LabeledComponent.create(new JTextField(), JUnitBundle.message("repeat.count.label"), BorderLayout.WEST);
    SettingsEditorFragment<JUnitConfiguration, LabeledComponent<JTextField>> countFragment =
      new SettingsEditorFragment<>("count", null, null, countField,
                                   (configuration, field) -> field.getComponent().setText(String.valueOf(configuration.getRepeatCount())),
                                   (configuration, field) -> {
                                     try {
                                       configuration.setRepeatCount(Integer.parseInt(field.getComponent().getText()));
                                     }
                                     catch (NumberFormatException e) {
                                       configuration.setRepeatCount(1);
                                     }
                                   },
                                   configuration -> RepeatCount.N.equals(configuration.getRepeatMode()));
    fragments.add(countFragment);
    repeat.addSettingsEditorListener(editor -> {
      boolean repeatN = RepeatCount.N.equals(repeat.getSelectedVariant());
      if (repeatN) repeat.component().setVisible(false);
      countFragment.setSelected(repeatN);
    });
    repeat.setToggleListener(s -> {
      if (RepeatCount.N.equals(s)) {
        IdeFocusManager.getInstance(getProject()).requestFocus(countFragment.getEditorComponent(), false);
      }
    });

    Supplier<String[]> variantsProvider = () -> JUnitConfigurable.getForkModel(testKind.getTestKind(), repeat.getSelectedVariant());
    VariantTagFragment<JUnitConfiguration, String> forkMode =
      VariantTagFragment.createFragment("forkMode", JUnitBundle.message("fork.mode.name"), group, variantsProvider,
                                        configuration -> configuration.getForkMode(),
                                        (configuration, s) -> configuration.setForkMode(s),
                                        configuration -> !FORK_NONE.equals(configuration.getForkMode()));
    forkMode.setVariantNameProvider(s -> JUnitBundle.message("junit.configuration.fork.mode." + s.toLowerCase(Locale.ENGLISH)));
    fragments.add(forkMode);

    SettingsEditorFragment<JUnitConfiguration, ?> asyncStackTraceForExceptions =
      SettingsEditorFragment.createTag("asyncStackTraceForExceptions", JUnitBundle.message("async.stack.trace.for.exceptions.name"), group,
                                       settings -> settings.isPrintAsyncStackTraceForExceptions(),
                                       (settings, value) -> settings.setPrintAsyncStackTraceForExceptions(value));
    fragments.add(asyncStackTraceForExceptions);

    testKind.addSettingsEditorListener(
      editor -> {
        int selectedType = testKind.getTestKind();
        forkMode.setSelectedVariant(JUnitConfigurable.updateForkMethod(selectedType, forkMode.getSelectedVariant(),
                                                                       repeat.getSelectedVariant()));
        scopeFragment.setRemovable(selectedType == JUnitConfigurationModel.PATTERN ||
                                   selectedType == JUnitConfigurationModel.ALL_IN_PACKAGE ||
                                   selectedType == JUnitConfigurationModel.TAGS ||
                                   selectedType == JUnitConfigurationModel.CATEGORY);
      });
    fragments.add(new TargetPathFragment<>());
  }

  @Override
  public boolean isInplaceValidationSupported() {
    return true;
  }
}
