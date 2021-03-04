// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.ConfigurationManager;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.ui.*;
import com.intellij.util.ui.CheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("ComponentNotRegistered")
public class ReferenceFilter extends FilterAction {

  public ReferenceFilter() {
    super(SSRBundle.messagePointer("reference.filter.name"));
  }

  @Override
  public boolean hasFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    return variable != null && !StringUtil.isEmpty(variable.getReferenceConstraint());
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    if (variable == null) {
      return;
    }
    variable.setReferenceConstraintName("");
    variable.setReferenceConstraint("");
    variable.setInvertReference(false);
  }

  @Override
  public boolean isApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    return myTable.getVariable() instanceof MatchVariableConstraint &&
           isApplicableConstraint(UIUtil.REFERENCE, nodes, completePattern, target);
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint constraint = myTable.getMatchVariable();
    if (constraint == null) {
      return;
    }
    final Configuration referencedConfiguration =
      ConfigurationManager.getInstance(myTable.getProject()).findConfigurationByName(constraint.getReferenceConstraint());
    if (referencedConfiguration != null) {
      component
        .append(SSRBundle.message("reference.0.label", (constraint.isInvertReference() ? "!" : "") + referencedConfiguration.getName()));
    }
  }

  @Override
  public FilterEditor<MatchVariableConstraint> getEditor() {
    return new FilterEditor<>(myTable.getMatchVariable(), myTable.getConstraintChangedCallback()) {

      private final JLabel myLabel = new JLabel(SSRBundle.message("reference.label"));
      private final @NotNull ComboBox<Configuration> myComboBox =
        new ComboBox<>(new CollectionComboBoxModel<>(ConfigurationManager.getInstance(myTable.getProject()).getAllConfigurations()));
      private final SimpleListCellRenderer<Configuration> renderer = new SimpleListCellRenderer<>() {
        @Override
        public void customize(@NotNull JList list, Configuration value, int index, boolean selected, boolean hasFocus) {
          setIcon(value.getIcon());
          setText(value.getName());
        }
      };
      private final ContextHelpLabel myHelpLabel = ContextHelpLabel.create(SSRBundle.message("reference.filter.help.text"));
      private final CheckBox myCheckBox = new CheckBox(SSRBundle.message("invert.filter"), this, "inverseTemplate");
      private final Component myGlue = Box.createGlue();
      public boolean inverseTemplate = myConstraint.isInvertReference();

      @Override
      protected void layoutComponents() {
        myComboBox.setRenderer(renderer);
        myComboBox.setPreferredSize(new Dimension(160, 28));
        myComboBox.setSwingPopup(false);

        final GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
          layout.createParallelGroup()
            .addGroup(layout.createSequentialGroup()
                        .addComponent(myLabel)
                        .addComponent(myComboBox)
            )
            .addGroup(layout.createSequentialGroup()
                        .addComponent(myCheckBox)
                        .addComponent(myGlue)
                        .addComponent(myHelpLabel)
            )
        );
        layout.setVerticalGroup(
          layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addComponent(myLabel)
                        .addComponent(myComboBox)
            )
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
                        .addComponent(myCheckBox)
                        .addComponent(myGlue)
                        .addComponent(myHelpLabel)
            )
        );
      }

      @Override
      protected void loadValues() {
        inverseTemplate = myConstraint.isInvertReference();
        myCheckBox.setSelected(inverseTemplate);
        var referencedTemplate =
          ConfigurationManager.getInstance(myTable.getProject()).findConfigurationByName(myConstraint.getReferenceConstraint());
        if (referencedTemplate != null) {
          myComboBox.setSelectedItem(referencedTemplate);
        }
      }

      @Override
      protected void saveValues() {
        final Configuration item = myComboBox.getItem();
        if (item != null) {
          myConstraint.setReferenceConstraint(item.getRefName());
          myConstraint.setReferenceConstraintName(item.getName());
          myConstraint.setInvertReference(inverseTemplate);
        }
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return myComboBox;
      }

      @Override
      public JComponent[] getFocusableComponents() {
        return new JComponent[]{myComboBox, myCheckBox};
      }
    };
  }
}
