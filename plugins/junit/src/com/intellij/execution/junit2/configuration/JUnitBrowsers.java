// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.junit2.configuration;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.JUnitBundle;
import com.intellij.execution.MethodBrowser;
import com.intellij.execution.configuration.BrowseModuleValueActionListener;
import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.execution.junit.TestClassFilter;
import com.intellij.execution.testframework.SourceScope;
import com.intellij.execution.ui.ClassBrowser;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.EditorTextFieldWithBrowseButton;
import com.intellij.util.indexing.DumbModeAccessType;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JTextField;
import java.awt.event.ActionEvent;
import java.util.Objects;
import java.util.function.Supplier;

import static com.intellij.execution.junit2.configuration.JUnitConfigurationModel.ALL_IN_PACKAGE;
import static com.intellij.execution.junit2.configuration.JUnitConfigurationModel.BY_SOURCE_CHANGES;
import static com.intellij.execution.junit2.configuration.JUnitConfigurationModel.CATEGORY;
import static com.intellij.execution.junit2.configuration.JUnitConfigurationModel.CLASS;
import static com.intellij.execution.junit2.configuration.JUnitConfigurationModel.DIR;
import static com.intellij.execution.junit2.configuration.JUnitConfigurationModel.METHOD;
import static com.intellij.execution.junit2.configuration.JUnitConfigurationModel.PATTERN;

/**
 * Browse actions for the test kind fields of the JUnit run configuration editor.
 *
 * @see JUnitTestKindFragment
 */
final class JUnitBrowsers {
  private JUnitBrowsers() {
  }

  /**
   * @return browsers indexed by the test kind constants of {@link JUnitConfigurationModel}. The array covers every kind,
   * with {@code null} for the kinds whose field has no browse button.
   */
  static BrowseModuleValueActionListener<?>[] createBrowsers(Project project,
                                                             ConfigurationModuleSelector moduleSelector,
                                                             EditorTextFieldWithBrowseButton packageField,
                                                             TextFieldWithBrowseButton patternField,
                                                             EditorTextFieldWithBrowseButton categoryField,
                                                             Supplier<String> className) {
    BrowseModuleValueActionListener<?>[] browsers = new BrowseModuleValueActionListener<?>[BY_SOURCE_CHANGES + 1];
    browsers[ALL_IN_PACKAGE] = new PackageChooserActionListener(project);
    browsers[CLASS] = new TestClassBrowser<EditorTextField>(project, moduleSelector, packageField);
    browsers[METHOD] = new MethodBrowser(project) {
        @Override
        protected Condition<PsiMethod> getFilter(PsiClass testClass) {
          return new JUnitUtil.TestMethodFilter(testClass);
        }

        @Override
        protected String getClassName() {
          return className.get();
        }

        @Override
        protected ConfigurationModuleSelector getModuleSelector() {
          return moduleSelector;
        }
      };
    browsers[PATTERN] = new TestsChooserActionListener(project, moduleSelector, packageField, patternField);
    browsers[DIR] = new BrowseModuleValueActionListener<JTextField>(project) {
      @Override
      protected String showDialog() {
        final VirtualFile virtualFile =
          FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project, null);
        if (virtualFile != null) {
          return FileUtil.toSystemDependentName(virtualFile.getPath());
        }
        return null;
      }
    };
    browsers[CATEGORY] = new CategoryBrowser(project, moduleSelector, categoryField);
    // UNIQUE_ID, TAGS, BY_SOURCE_POSITION and BY_SOURCE_CHANGES have no browse button
    return browsers;
  }

  static @NotNull JavaCodeFragment.VisibilityChecker createClassVisibilityChecker(TestClassBrowser<?> classBrowser) {
    return new JavaCodeFragment.VisibilityChecker() {
      @Override
      public Visibility isDeclarationVisible(PsiElement declaration, PsiElement place) {
        try {
          if (declaration instanceof PsiClass &&
              (classBrowser.getFilter().isAccepted(((PsiClass)declaration)) ||
               classBrowser.findClass(((PsiClass)declaration).getQualifiedName()) != null && place.getParent() != null)) {
            return Visibility.VISIBLE;
          }
        }
        catch (ClassBrowser.NoFilterException e) {
          return Visibility.NOT_VISIBLE;
        }
        return Visibility.NOT_VISIBLE;
      }
    };
  }

  private static class PackageChooserActionListener extends BrowseModuleValueActionListener<EditorTextField> {
    PackageChooserActionListener(final Project project) {
      super(project);
    }

    @Override
    protected String showDialog() {
      final PackageChooserDialog dialog = new PackageChooserDialog(ExecutionBundle.message("choose.package.dialog.title"), getProject());
      dialog.show();
      final PsiPackage aPackage = dialog.getSelectedPackage();
      return aPackage != null ? aPackage.getQualifiedName() : null;
    }
  }

  private static class TestsChooserActionListener extends TestClassBrowser<JTextField> {
    private final TextFieldWithBrowseButton myPatternTextField;

    TestsChooserActionListener(final Project project, ConfigurationModuleSelector moduleSelector,
                               EditorTextFieldWithBrowseButton packageField, TextFieldWithBrowseButton patternField) {
      super(project, moduleSelector, packageField);
      myPatternTextField = patternField;
    }

    @Override
    protected void onClassChosen(@NotNull PsiClass psiClass) {
      final JTextField textField = myPatternTextField.getTextField();
      final String text = textField.getText();
      textField.setText(text + (!text.isEmpty() ? "||" : "") + psiClass.getQualifiedName());
    }

    @Override
    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      return DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
        try {
          return TestClassFilter.create(SourceScope.wholeProject(getProject()), null);
        }
        catch (JUnitUtil.NoJUnitException e) {
          throw new NoFilterException(new MessagesEx.MessageInfo(getProject(),
                                                                 e.getMessage(),
                                                                 JUnitBundle.message("cannot.browse.test.inheritors.dialog.title")));
        }
      });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      showDialog();
    }
  }

  static class TestClassBrowser<T extends JComponent> extends ClassBrowser<T> {
    private final ConfigurationModuleSelector myModuleSelector;
    private final EditorTextFieldWithBrowseButton myPackageTextField;

    TestClassBrowser(final Project project, ConfigurationModuleSelector moduleSelector, EditorTextFieldWithBrowseButton packageTextField) {
      super(project, ExecutionBundle.message("choose.test.class.dialog.title"));
      myModuleSelector = moduleSelector;
      myPackageTextField = packageTextField;
    }

    @Override
    protected void onClassChosen(@NotNull PsiClass psiClass) {
      myPackageTextField.setText(StringUtil.getPackageName(Objects.requireNonNull(psiClass.getQualifiedName())));
    }

    @Override
    protected PsiClass findClass(final String className) {
      return myModuleSelector.findClass(className);
    }

    @Override
    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      final Module module = getModule();
      final ClassFilter.ClassFilterWithScope classFilter;
      try {
        final JUnitConfiguration configurationCopy =
          new JUnitConfiguration(JUnitBundle.message("default.junit.configuration.name"), getProject());
        myModuleSelector.applyTo(configurationCopy);
        SourceScope sourceScope = SourceScope.modulesWithDependencies(configurationCopy.getModules());
        GlobalSearchScope globalSearchScope = sourceScope.getGlobalSearchScope();
        if (JUnitUtil.isJUnit5(globalSearchScope, getProject())) {
          return new ClassFilter.ClassFilterWithScope() {
            @Override
            public GlobalSearchScope getScope() {
              return globalSearchScope;
            }

            @Override
            public boolean isAccepted(PsiClass aClass) {
              return JUnitUtil.isTestClass(aClass, true, true);
            }
          };
        }
        classFilter = DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(
          () -> TestClassFilter.create(sourceScope, configurationCopy.getConfigurationModule().getModule()));
      }
      catch (JUnitUtil.NoJUnitException e) {
        throw new NoFilterException(new MessagesEx.MessageInfo(
          module.getProject(),
          JUnitBundle.message("junit.not.found.in.module.error.message", module.getName()),
          JUnitBundle.message("cannot.browse.test.inheritors.dialog.title")));
      }
      return classFilter;
    }

    private @NotNull Module getModule() throws NoFilterException {
      final Module module = myModuleSelector.getModule();
      if (module != null) return module;
      final Project project = myModuleSelector.getProject();
      final String moduleName = myModuleSelector.getModuleName();
      throw new NoFilterException(new MessagesEx.MessageInfo(
        project,
        moduleName.isEmpty() ? JUnitBundle.message("no.module.selected.error.message")
                             : JUnitBundle.message("module.does.not.exists", moduleName, project.getName()),
        JUnitBundle.message("cannot.browse.test.inheritors.dialog.title")));
    }
  }

  private static class CategoryBrowser extends ClassBrowser<EditorTextField> {
    private final ConfigurationModuleSelector myModuleSelector;
    private final EditorTextFieldWithBrowseButton myCategoryField;

    CategoryBrowser(Project project, ConfigurationModuleSelector moduleSelector, EditorTextFieldWithBrowseButton categoryField) {
      super(project, JUnitBundle.message("category.interface.dialog.title"));
      myModuleSelector = moduleSelector;
      myCategoryField = categoryField;
    }

    @Override
    protected PsiClass findClass(final String className) {
      return myModuleSelector.findClass(className);
    }

    @Override
    protected ClassFilter.ClassFilterWithScope getFilter() throws NoFilterException {
      final Module module = myModuleSelector.getModule();
      final GlobalSearchScope scope;
      if (module == null) {
        scope = GlobalSearchScope.allScope(getProject());
      }
      else {
        scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
      }
      return new ClassFilter.ClassFilterWithScope() {
        @Override
        public GlobalSearchScope getScope() {
          return scope;
        }

        @Override
        public boolean isAccepted(final PsiClass aClass) {
          return true;
        }
      };
    }

    @Override
    protected void onClassChosen(@NotNull PsiClass psiClass) {
      myCategoryField.setText(psiClass.getQualifiedName());
    }
  }
}
