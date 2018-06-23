// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.fields.IntegerField;

import javax.swing.*;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class CountFilter extends FilterAction {

  boolean myMinZero;
  boolean myMaxUnlimited;

  public CountFilter(FilterTable filterTable) {
    super("Count", filterTable);
  }

  @Override
  public boolean hasFilter() {
    final MatchVariableConstraint constraint = myTable.getConstraint();
    return constraint.getMinCount() != 1 || constraint.getMaxCount() != 1;
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint constraint = myTable.getConstraint();
    constraint.setMinCount(1);
    constraint.setMaxCount(1);
  }

  @Override
  public boolean isApplicable(List<PsiElement> nodes, boolean completePattern, boolean target) {
    final StructuralSearchProfile profile = myTable.getProfile();
    myMinZero = profile.isApplicableConstraint(UIUtil.MINIMUM_ZERO, nodes, completePattern, false);
    myMaxUnlimited = profile.isApplicableConstraint(UIUtil.MAXIMUM_UNLIMITED, nodes, completePattern, false);
    return myMinZero || myMaxUnlimited;
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint constraint = myTable.getConstraint();
    final int min = constraint.getMinCount();
    final int max = constraint.getMaxCount();
    myLabel.append("count=[" + min + "," + (max == Integer.MAX_VALUE ? "∞" : max) + ']');
  }

  @Override
  public FilterEditor getEditor() {
    return new FilterEditor(myTable.getConstraint()) {

      private final IntegerField myMinField = new IntegerField();
      private final IntegerField myMaxField = new IntegerField();
      private final JLabel myMinLabel = new JLabel("min=");
      private final JLabel myMaxLabel = new JLabel("max=");

      {
        myMinField.getValueEditor().addListener(newValue -> {
          if (myMinField.getValueEditor().isValid(newValue) && myMaxField.getValue() < newValue) myMaxField.setValue(newValue);
        });
        myMaxField.getValueEditor().addListener(newValue -> {
          if (myMaxField.getValueEditor().isValid(newValue) && myMinField.getValue() > newValue) myMinField.setValue(newValue);
        });
      }

      protected void layoutComponents() {
        final GroupLayout layout = new GroupLayout(this);
        setLayout(layout);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(
          layout.createSequentialGroup()
                .addComponent(myMinLabel)
                .addComponent(myMinField)
                .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED, 20, 20)
                .addComponent(myMaxLabel)
                .addComponent(myMaxField)
        );
        layout.setVerticalGroup(
          layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                .addComponent(myMinLabel)
                .addComponent(myMinField)
                .addComponent(myMaxLabel)
                .addComponent(myMaxField)
        );
      }

      @Override
      protected void loadValues() {
        myMinField.setMinValue(myMinZero ? 0 : 1);
        myMinField.setMaxValue(myMaxUnlimited ? Integer.MAX_VALUE : 1);
        myMinField.setDefaultValue(myMinZero ? 0 : 1);
        myMinField.setDefaultValueText(myMinZero ? "0" : "1");
        myMinField.setValue(myConstraint.getMinCount());
        myMinField.selectAll();

        myMaxField.setMinValue(myMinZero ? 0 : 1);
        myMaxField.setMaxValue(myMaxUnlimited ? Integer.MAX_VALUE : 1);
        myMaxField.setDefaultValue(myMaxUnlimited ? Integer.MAX_VALUE : 1);
        myMaxField.setDefaultValueText(myMaxUnlimited ? SSRBundle.message("editvarcontraints.unlimited") : "1");
        myMaxField.setValue(myConstraint.getMaxCount());
        myMaxField.selectAll();
      }

      @Override
      public void saveValues() {
        myConstraint.setMinCount(myMinField.getValue());
        myConstraint.setMaxCount(myMaxField.getValue());
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return myMinField;
      }

      @Override
      public JComponent[] getFocusableComponents() {
        return new IntegerField[]{myMinField, myMaxField};
      }
    };
  }
}
