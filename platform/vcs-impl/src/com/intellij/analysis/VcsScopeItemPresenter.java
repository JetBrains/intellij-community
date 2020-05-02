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
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
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
    ComboBox<String> myChangeLists = new ComboBox<>();
    myChangeLists.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
      int availableWidth = myChangeLists.getWidth(); // todo, is it correct?
      if (availableWidth <= 0) {
        availableWidth = JBUIScale.scale(200);
      }
      if (label.getFontMetrics(label.getFont()).stringWidth(value) < availableWidth) {
        label.setText(value);
      }
      else {
        label.setText(StringUtil.trimLog(value, 50));
      }
    }));

    myChangeLists.setModel(model.getChangeListsModel());
    myChangeLists.setEnabled(button.isSelected());
    button.addItemListener(e -> myChangeLists.setEnabled(button.isSelected()));
    ArrayList<JComponent> components = new ArrayList<>();
    components.add(myChangeLists);
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
    if (ChangeListManager.getInstance(project).getAffectedFiles().isEmpty()) {
      return null;
    }

    return new VcsScopeItem(project);
  }
}