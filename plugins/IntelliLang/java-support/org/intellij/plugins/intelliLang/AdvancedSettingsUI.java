// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.intellij.plugins.intelliLang;

import com.intellij.ide.util.ClassFilter;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ReferenceEditorWithBrowseButton;
import org.intellij.plugins.intelliLang.util.PsiUtilEx;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;

/**
 * @author Gregory.Shrago
 */
public class AdvancedSettingsUI implements SearchableConfigurable {
  private final Configuration.AdvancedConfiguration myConfiguration;
  private AdvancedSettingsPanel myPanel;
  private final Project myProject;

  public AdvancedSettingsUI(@NotNull final Project project, Configuration configuration) {
    myProject = project;
    myConfiguration = configuration.getAdvancedConfiguration();
  }

  @Override
  public JComponent createComponent() {
    myPanel = new AdvancedSettingsPanel();
    return myPanel.myRoot;
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  @Override
  public String getDisplayName() {
    return IntelliLangBundle.message("configurable.AdvancedSettingsUI.display.name");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.injection.advanced";
  }

  @NotNull
  @Override
  public String getId() {
    return "IntelliLang.Advanced";
  }

  private static final class BrowseClassListener implements ActionListener {
    private final Project myProject;
    private final ReferenceEditorWithBrowseButton myField;

    private BrowseClassListener(Project project, ReferenceEditorWithBrowseButton annotationField) {
      myProject = project;
      myField = annotationField;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(myProject);

      final GlobalSearchScope scope = GlobalSearchScope.allScope(myProject);
      final PsiClass aClass = JavaPsiFacade.getInstance(myProject).findClass(myField.getText(), scope);
      final TreeClassChooser chooser =
        factory.createNoInnerClassesScopeChooser(IntelliLangBundle.message("dialog.title.select.annotation.class"), scope, new ClassFilter() {
          @Override
          public boolean isAccepted(PsiClass aClass) {
            return aClass.isAnnotationType();
          }
        }, aClass);

      chooser.showDialog();
      final PsiClass psiClass = chooser.getSelected();
      if (psiClass != null) {
        myField.setText(psiClass.getQualifiedName());
      }
    }
  }

  public class AdvancedSettingsPanel {
    @SuppressWarnings({"UnusedDeclaration"})
    private JPanel myRoot;

    private JRadioButton myNoInstrumentation;
    private JRadioButton myAssertInstrumentation;
    private JRadioButton myExceptionInstrumentation;
    private JPanel myLanguageAnnotationPanel;
    private JPanel myPatternAnnotationPanel;
    private JPanel mySubstAnnotationPanel;
    private JRadioButton myDfaOff;
    private JRadioButton myAnalyzeReferences;
    private JRadioButton myUseDfa;
    private JRadioButton myLookForAssignments;
    private JCheckBox myIncludeUncomputableOperandsAsCheckBox;
    private JCheckBox mySourceModificationAllowedCheckBox;

    private final ReferenceEditorWithBrowseButton myAnnotationField;
    private final ReferenceEditorWithBrowseButton myPatternField;
    private final ReferenceEditorWithBrowseButton mySubstField;

    public AdvancedSettingsPanel() {
      myAnnotationField = new ReferenceEditorWithBrowseButton(null, myProject, s -> PsiUtilEx.createDocument(s, myProject), myConfiguration.getLanguageAnnotationClass());
      myAnnotationField.addActionListener(new BrowseClassListener(myProject, myAnnotationField));
      myAnnotationField.setEnabled(!myProject.isDefault());
      addField(myLanguageAnnotationPanel, myAnnotationField);

      myPatternField = new ReferenceEditorWithBrowseButton(null, myProject, s -> PsiUtilEx.createDocument(s, myProject), myConfiguration.getPatternAnnotationClass());
      myPatternField.addActionListener(new BrowseClassListener(myProject, myPatternField));
      myPatternField.setEnabled(!myProject.isDefault());
      addField(myPatternAnnotationPanel, myPatternField);

      mySubstField = new ReferenceEditorWithBrowseButton(null, myProject, s -> PsiUtilEx.createDocument(s, myProject), myConfiguration.getPatternAnnotationClass());
      mySubstField.addActionListener(new BrowseClassListener(myProject, mySubstField));
      mySubstField.setEnabled(!myProject.isDefault());
      addField(mySubstAnnotationPanel, mySubstField);
    }
    //

    /**
     * Adds textfield into placeholder panel and assigns a directly preceding label
     */
    private void addField(JPanel panel, ReferenceEditorWithBrowseButton field) {
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
      if (!myConfiguration.getDfaOption().equals(getDfaOption())) {
        return true;
      }
      if (myConfiguration.isIncludeUncomputablesAsLiterals() != myIncludeUncomputableOperandsAsCheckBox.isSelected()) {
        return true;
      }
      if (myConfiguration.isSourceModificationAllowed() != mySourceModificationAllowedCheckBox.isSelected()) {
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

      myConfiguration.setDfaOption(getDfaOption());
      myConfiguration.setIncludeUncomputablesAsLiterals(myIncludeUncomputableOperandsAsCheckBox.isSelected());
      myConfiguration.setSourceModificationAllowed(mySourceModificationAllowedCheckBox.isSelected());
    }

    @NotNull
    private Configuration.DfaOption getDfaOption() {
      if (myDfaOff.isSelected()) return Configuration.DfaOption.OFF;
      if (myAnalyzeReferences.isSelected()) return Configuration.DfaOption.RESOLVE;
      if (myLookForAssignments.isSelected()) return Configuration.DfaOption.ASSIGNMENTS;
      if (myUseDfa.isSelected()) return Configuration.DfaOption.DFA;
      return Configuration.DfaOption.OFF;
    }

    public void reset() {
      myAnnotationField.setText(myConfiguration.getLanguageAnnotationClass());
      myPatternField.setText(myConfiguration.getPatternAnnotationClass());
      mySubstField.setText(myConfiguration.getSubstAnnotationClass());

      myNoInstrumentation.setSelected(myConfiguration.getInstrumentation() == Configuration.InstrumentationType.NONE);
      myAssertInstrumentation.setSelected(myConfiguration.getInstrumentation() == Configuration.InstrumentationType.ASSERT);
      myExceptionInstrumentation.setSelected(myConfiguration.getInstrumentation() == Configuration.InstrumentationType.EXCEPTION);

      setDfaOption(myConfiguration.getDfaOption());
      myIncludeUncomputableOperandsAsCheckBox.setSelected(myConfiguration.isIncludeUncomputablesAsLiterals());
      mySourceModificationAllowedCheckBox.setSelected(myConfiguration.isSourceModificationAllowed());
    }

    private void setDfaOption(@NotNull final Configuration.DfaOption dfaOption) {
      switch (dfaOption) {
        case OFF -> myDfaOff.setSelected(true);
        case RESOLVE -> myAnalyzeReferences.setSelected(true);
        case ASSIGNMENTS -> myLookForAssignments.setSelected(true);
        case DFA -> myUseDfa.setSelected(true);
      }
    }
  }
}
