// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage;

import com.intellij.codeInsight.AnnotationsPanel;
import com.intellij.java.coverage.JavaCoverageBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.PsiClass;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;

final class JavaCoverageOptions extends CoverageOptions {
  private final Project myProject;
  private final JavaCoverageOptionsProvider myCoverageOptionsProvider;
  private JavaCoverageOptionsEditor myEditor;

  JavaCoverageOptions(@NotNull Project project) {
    myProject = project;
    myCoverageOptionsProvider = JavaCoverageOptionsProvider.getInstance(project);
  }

  @Override
  public JComponent createComponent() {
    myEditor = new JavaCoverageOptionsEditor(myProject, myCoverageOptionsProvider);
    return myEditor.getComponent();
  }

  @Override
  public boolean isModified() {
    return myEditor.isModified(myCoverageOptionsProvider);
  }

  @Override
  public void apply() {
    myEditor.apply(myCoverageOptionsProvider);
  }

  @Override
  public void reset() {
    myEditor.reset(myCoverageOptionsProvider);
  }

  @Override
  public void disposeUIResources() {
    myEditor = null;
  }

  private static class JavaCoverageOptionsEditor {

    private final JPanel myPanel = new JPanel(new VerticalFlowLayout());
    private final JCheckBox myImplicitCheckBox =
      new JCheckBox(JavaCoverageBundle.message("settings.coverage.java.ignore.implicitly.declared.default.constructors"), true);
    private final JCheckBox myEmptyCheckBox =
      new JCheckBox(JavaCoverageBundle.message("settings.coverage.java.ignore.empty.private.constructors.of.utility.classes"), true);

    private final AnnotationsPanel myExcludeAnnotationsPanel;

    JavaCoverageOptionsEditor(Project project, JavaCoverageOptionsProvider coverageOptionsProvider) {
      myPanel.setBorder(IdeBorderFactory.createTitledBorder(JavaCoverageBundle.message("settings.coverage.java.java.coverage")));
      myPanel.add(myImplicitCheckBox);
      myPanel.add(myEmptyCheckBox);
      myExcludeAnnotationsPanel = new AnnotationsPanel(
        project, "Exclude", "",
        coverageOptionsProvider.getExcludeAnnotationPatterns(),
        JavaCoverageOptionsProvider.getDefaultExcludeAnnotationPatterns(),
        Collections.emptySet(), false, false
      ) {
        @Override
        protected boolean isAnnotationAccepted(PsiClass annotation) {
          return annotation.getContainingClass() == null;
        }
      };
      myPanel.add(Box.createVerticalStrut(5));
      myPanel.add(myExcludeAnnotationsPanel.getComponent());
    }

    public JPanel getComponent() {
      return myPanel;
    }

    public boolean isModified(JavaCoverageOptionsProvider provider) {
      return myImplicitCheckBox.isSelected() != provider.ignoreImplicitConstructors() ||
             myEmptyCheckBox.isSelected() != provider.ignoreEmptyPrivateConstructors() ||
             !Arrays.equals(myExcludeAnnotationsPanel.getAnnotations(), ArrayUtil.toStringArray(provider.getExcludeAnnotationPatterns()));
    }

    public void apply(JavaCoverageOptionsProvider provider) {
      provider.setIgnoreImplicitConstructors(myImplicitCheckBox.isSelected());
      provider.setIgnoreEmptyPrivateConstructors(myEmptyCheckBox.isSelected());
      provider.setExcludeAnnotationPatterns(Arrays.asList(myExcludeAnnotationsPanel.getAnnotations()));
    }

    public void reset(JavaCoverageOptionsProvider provider) {
      myImplicitCheckBox.setSelected(provider.ignoreImplicitConstructors());
      myEmptyCheckBox.setSelected(provider.ignoreEmptyPrivateConstructors());
      myExcludeAnnotationsPanel.resetAnnotations(provider.getExcludeAnnotationPatterns());
    }
  }
}
