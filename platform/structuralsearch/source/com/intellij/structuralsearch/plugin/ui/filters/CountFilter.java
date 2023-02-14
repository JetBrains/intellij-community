// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.MatchVariableConstraint;
import com.intellij.structuralsearch.NamedScriptableDefinition;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.fields.IntegerField;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
@SuppressWarnings("ComponentNotRegistered")
public class CountFilter extends FilterAction {

  boolean myMinZero;
  boolean myMaxUnlimited;

  public CountFilter() {
    super(SSRBundle.messagePointer("count.filter.name"));
  }

  @Override
  public @NotNull String getShortText(NamedScriptableDefinition variable) {
    if (!(variable instanceof MatchVariableConstraint constraint)) {
      return "";
    }
    final int minCount = constraint.getMinCount();
    final int maxCount = constraint.getMaxCount();
    if (minCount == 1 && maxCount == 1) {
      return "";
    }
    return SSRBundle.message("min.occurs.tooltip.message", minCount, (maxCount == Integer.MAX_VALUE) ? "∞" : maxCount);
  }

  @Override
  public boolean hasFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    if (variable == null) {
      return false;
    }
    return variable.getMinCount() != 1 || variable.getMaxCount() != 1;
  }

  @Override
  public void clearFilter() {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    if (variable == null) {
      return;
    }
    variable.setMinCount(1);
    variable.setMaxCount(1);
  }

  @Override
  public void initFilter() {
    final MatchVariableConstraint constraint = myTable.getMatchVariable();
    if (constraint == null) {
      return;
    }
    constraint.setMinCount(myMinZero ? 0 : 1);
    constraint.setMaxCount(myMaxUnlimited ? Integer.MAX_VALUE : 1);
  }

  @Override
  public boolean isApplicable(List<? extends PsiElement> nodes, boolean completePattern, boolean target) {
    if (myTable.getMatchVariable() == null) {
      return false;
    }
    myMinZero = isApplicableConstraint(UIUtil.MINIMUM_ZERO, nodes, completePattern, target);
    myMaxUnlimited = isApplicableConstraint(UIUtil.MAXIMUM_UNLIMITED, nodes, completePattern, target);
    return myMinZero || myMaxUnlimited;
  }

  @Override
  protected void setLabel(SimpleColoredComponent component) {
    final MatchVariableConstraint variable = myTable.getMatchVariable();
    if (variable == null) {
      return;
    }
    final int min = variable.getMinCount();
    final int max = variable.getMaxCount();
    myLabel.append(SSRBundle.message("count.label", "[" + min + "," + (max == Integer.MAX_VALUE ? "∞" : max) + ']'));
    if (min == 1 && max == 1) {
      myLabel.append(SSRBundle.message("default.label"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  @Override
  public FilterEditor<MatchVariableConstraint> getEditor() {
    return new FilterEditor<>(myTable.getMatchVariable(), myTable.getConstraintChangedCallback()) {

      private final IntegerField myMinField = new IntegerField();
      private final IntegerField myMaxField = new IntegerField();
      private final JLabel myMinLabel = new JLabel(SSRBundle.message("min.label"));
      private final JLabel myMaxLabel = new JLabel(SSRBundle.message("max.label"));

      @Override
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
        myMinField.getValueEditor().addListener(newValue -> {
          if (myMinField.getValueEditor().isValid(newValue) && myMaxField.getValue() < newValue) myMaxField.setValue(newValue);
        });
        myMaxField.getValueEditor().addListener(newValue -> {
          if (myMaxField.getValueEditor().isValid(newValue) && myMinField.getValue() > newValue) myMinField.setValue(newValue);
        });
      }

      @Override
      protected void loadValues() {
        myMinField.setMinValue(myMinZero ? 0 : 1);
        myMinField.setMaxValue(myMaxUnlimited ? Integer.MAX_VALUE : 1);
        myMinField.setDefaultValue(myMinZero ? 0 : 1);
        myMinField.setDefaultValueText(myMinZero ? "0" : "1");
        final int minCount = myConstraint.getMinCount();
        if (!isDefaultValue(minCount)) {
          myMinField.setValue(minCount);
        }
        myMinField.setCanBeEmpty(true);
        myMinField.selectAll();

        myMaxField.setMinValue(myMinZero ? 0 : 1);
        myMaxField.setMaxValue(myMaxUnlimited ? Integer.MAX_VALUE : 1);
        myMaxField.setDefaultValue(myMaxUnlimited ? Integer.MAX_VALUE : 1);
        myMaxField.setDefaultValueText(myMaxUnlimited ? SSRBundle.message("unlimited.placeholder") : "1");
        final int maxCount = myConstraint.getMaxCount();
        if (!isDefaultValue(maxCount)) {
          myMaxField.setValue(maxCount);
        }
        myMaxField.setCanBeEmpty(true);
        myMaxField.selectAll();
      }

      private boolean isDefaultValue(int count) {
        if (count == 0) {
          return myMinZero;
        }
        else if (count == 1) {
          return !myMinZero || !myMaxUnlimited;
        }
        else if (count == Integer.MAX_VALUE) {
          return myMaxUnlimited;
        }
        return false;
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
