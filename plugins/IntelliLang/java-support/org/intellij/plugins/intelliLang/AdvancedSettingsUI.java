/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.intellij.plugins.intelliLang;

import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import com.intellij.ui.ShiftTabAction;
import com.intellij.util.Function;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

/**
 * @author Gregory.Shrago
 */
public class AdvancedSettingsUI implements Configurable {
  private final Configuration myConfiguration;

  @SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
  private JPanel myRoot;

  private JRadioButton myNoInstrumentation;
  private JRadioButton myAssertInstrumentation;
  private JRadioButton myExceptionInstrumentation;
  private JPanel myLanguageAnnotationPanel;
  private JPanel myPatternAnnotationPanel;
  private JPanel mySubstAnnotationPanel;
  private JCheckBox myAnalyzeReferencesCheckBox;
  private JCheckBox myUseDataflowAnalysisIfCheckBox;
  private JCheckBox myIncludeUncomputableOperandsAsCheckBox;

  private final ReferenceEditorWithBrowseButton myAnnotationField;
  private final ReferenceEditorWithBrowseButton myPatternField;
  private final ReferenceEditorWithBrowseButton mySubstField;

  public AdvancedSettingsUI(@NotNull final Project project, Configuration configuration) {
    myConfiguration = configuration;

    myAnnotationField = new ReferenceEditorWithBrowseButton(null, project, new Function<String, Document>() {
      public Document fun(String s) {
        return PsiUtilEx.createDocument(s, project);
      }
    }, myConfiguration.getLanguageAnnotationClass());
    myAnnotationField.addActionListener(new BrowseClassListener(project, myAnnotationField));
    myAnnotationField.setEnabled(!project.isDefault());
    ShiftTabAction.attachTo(myAnnotationField.getEditorTextField());
    addField(myLanguageAnnotationPanel, myAnnotationField);

    myPatternField = new ReferenceEditorWithBrowseButton(null, project, new Function<String, Document>() {
      public Document fun(String s) {
        return PsiUtilEx.createDocument(s, project);
      }
    }, myConfiguration.getPatternAnnotationClass());
    myPatternField.addActionListener(new BrowseClassListener(project, myPatternField));
    myPatternField.setEnabled(!project.isDefault());
    ShiftTabAction.attachTo(myPatternField.getEditorTextField());
    addField(myPatternAnnotationPanel, myPatternField);

    mySubstField = new ReferenceEditorWithBrowseButton(null, project, new Function<String, Document>() {
      public Document fun(String s) {
        return PsiUtilEx.createDocument(s, project);
      }
    }, myConfiguration.getPatternAnnotationClass());
    mySubstField.addActionListener(new BrowseClassListener(project, mySubstField));
    mySubstField.setEnabled(!project.isDefault());
    ShiftTabAction.attachTo(mySubstField.getEditorTextField());
    addField(mySubstAnnotationPanel, mySubstField);
  }

  /**
   * Adds textfield into placeholder panel and assigns a directly preceding label
   */
  private static void addField(JPanel panel, ReferenceEditorWithBrowseButton field) {
    panel.add(field, BorderLayout.CENTER);

    final Component[] components = panel.getParent().getComponents();
    final int index = Arrays.asList(components).indexOf(panel);
    if (index > 0) {
      final Component component = components[index - 1];
      if (component instanceof JLabel) {
        ((JLabel)component).setLabelFor(field);
      }
    }
  }

  public JComponent createComponent() {
    return myRoot;
  }

  @SuppressWarnings({"SimplifiableIfStatement"})
  public boolean isModified() {
    if (getInstrumentation() != myConfiguration.getInstrumentation()) {
      return true;
    }
    if (!myAnnotationField.getText().equals(myConfiguration.getLanguageAnnotationClass())) {
      return true;
    }
    if (!myPatternField.getText().equals(myConfiguration.getPatternAnnotationClass())) {
      return true;
    }
    if (!mySubstField.getText().equals(myConfiguration.getSubstAnnotationClass())) {
      return true;
    }
    if (myConfiguration.isResolveReferences() != myAnalyzeReferencesCheckBox.isSelected()) {
      return true;
    }
    if (myConfiguration.isUseDfaIfAvailable() != myUseDataflowAnalysisIfCheckBox.isSelected()) {
      return true;
    }
    if (myConfiguration.isIncludeUncomputablesAsLiterals() != myIncludeUncomputableOperandsAsCheckBox.isSelected()) {
      return true;
    }
    return false;
  }

  @NotNull
  private Configuration.InstrumentationType getInstrumentation() {
    if (myNoInstrumentation.isSelected()) return Configuration.InstrumentationType.NONE;
    if (myAssertInstrumentation.isSelected()) return Configuration.InstrumentationType.ASSERT;
    if (myExceptionInstrumentation.isSelected()) return Configuration.InstrumentationType.EXCEPTION;

    assert false;
    return null;
  }

  public void apply() throws ConfigurationException {
    myConfiguration.setInstrumentationType(getInstrumentation());
    myConfiguration.setLanguageAnnotation(myAnnotationField.getText());
    myConfiguration.setPatternAnnotation(myPatternField.getText());
    myConfiguration.setSubstAnnotation(mySubstField.getText());

    myConfiguration.setResolveReferences(myAnalyzeReferencesCheckBox.isSelected());
    myConfiguration.setUseDfaIfAvailable(myUseDataflowAnalysisIfCheckBox.isSelected());
    myConfiguration.setIncludeUncomputablesAsLiterals(myIncludeUncomputableOperandsAsCheckBox.isSelected());
  }

  public void reset() {
    myAnnotationField.setText(myConfiguration.getLanguageAnnotationClass());
    myPatternField.setText(myConfiguration.getPatternAnnotationClass());
    mySubstField.setText(myConfiguration.getSubstAnnotationClass());

    myNoInstrumentation.setSelected(myConfiguration.getInstrumentation() == Configuration.InstrumentationType.NONE);
    myAssertInstrumentation.setSelected(myConfiguration.getInstrumentation() == Configuration.InstrumentationType.ASSERT);
    myExceptionInstrumentation.setSelected(myConfiguration.getInstrumentation() == Configuration.InstrumentationType.EXCEPTION);

    myAnalyzeReferencesCheckBox.setSelected(myConfiguration.isResolveReferences());
    myUseDataflowAnalysisIfCheckBox.setSelected(myConfiguration.isUseDfaIfAvailable());
    myIncludeUncomputableOperandsAsCheckBox.setSelected(myConfiguration.isIncludeUncomputablesAsLiterals());
  }

  public void disposeUIResources() {
  }

  @Nls
  public String getDisplayName() {
    return "Advanced";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.settings.injection.advanced";
  }

  private static class BrowseClassListener implements ActionListener {
    private final Project myProject;
    private final ReferenceEditorWithBrowseButton myField;

    private BrowseClassListener(Project project, ReferenceEditorWithBrowseButton annotationField) {
      myProject = project;
      myField = annotationField;
    }

    public void actionPerformed(ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);

      final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(myField.getText(), scope);
      final TreeClassChooser chooser =
        factory.createNoInnerClassesScopeChooser("Select Annotation Class", scope, new TreeClassChooser.ClassFilter() {
          public boolean isAccepted(PsiClass aClass) {
            return aClass.isAnnotationType();
          }
        }, aClass);

      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelectedClass();
      if (psiClass != null) {
        myField.setText(psiClass.getQualifiedName());
      }
    }
  }
}
