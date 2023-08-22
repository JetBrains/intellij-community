// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis;

import com.intellij.analysis.dialog.ModelScopeItem;
import com.intellij.analysis.dialog.ModelScopeItemPresenter;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VcsScopeItemPresenter implements ModelScopeItemPresenter {

  @Override
  public int getScopeId() {
    return AnalysisScope.UNCOMMITTED_FILES;
  }

  @NotNull
  @Override
  public JRadioButton getButton(ModelScopeItem m) {
    JRadioButton button = new JRadioButton();
    button.setText(CodeInsightBundle.message("scope.option.uncommitted.files"));
    return button;
  }

  @NotNull
  @Override
  public List<JComponent> getAdditionalComponents(JRadioButton button, ModelScopeItem m, Disposable dialogDisposable) {
    VcsScopeItem model = (VcsScopeItem)m;
    DefaultComboBoxModel<LocalChangeList> comboBoxModel = model.getChangeListsModel();
    if (comboBoxModel == null) {
      return Collections.emptyList();
    }

    ComboBox<LocalChangeList> comboBox = new ComboBox<>();
    comboBox.setRenderer(SimpleListCellRenderer.create((@NotNull JBLabel label, @Nullable LocalChangeList value, int index) -> {
      int availableWidth = comboBox.getWidth(); // todo, is it correct?
      if (availableWidth <= 0) {
        availableWidth = JBUIScale.scale(200);
      }
      String text = value != null ? value.getName() : CodeInsightBundle.message("scope.option.uncommitted.files.all.changelists.choice");
      if (label.getFontMetrics(label.getFont()).stringWidth(text) >= availableWidth) {
        text = StringUtil.trimLog(text, 50);
      }
      label.setText(text);
    }));

    comboBox.setModel(comboBoxModel);
    comboBox.setEnabled(button.isSelected());
    button.addItemListener(e -> comboBox.setEnabled(button.isSelected()));
    ArrayList<JComponent> components = new ArrayList<>();
    components.add(comboBox);
    return components;
  }

  @Override
  public boolean isApplicable(ModelScopeItem model) {
    return model instanceof VcsScopeItem;
  }

  @Override
  public @Nullable ModelScopeItem tryCreate(@NotNull Project project,
                                            @NotNull AnalysisScope scope,
                                            @Nullable Module module,
                                            @Nullable PsiElement context) {
    return VcsScopeItem.createIfHasVCS(project);
  }
}