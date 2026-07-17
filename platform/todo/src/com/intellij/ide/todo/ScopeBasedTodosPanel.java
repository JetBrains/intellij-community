// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.todo.scopeChooser.FrontendTodoScopeChooserImpl;
import com.intellij.ide.todo.scopeChooser.TodoScopeChooser;
import com.intellij.ide.todo.scopeChooser.TodoScopeChooserImpl;
import com.intellij.ide.util.scopeChooser.FrontendScopeChooser;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopesFilterConditionType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBBox;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTree;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import static com.intellij.ide.todo.TodoImplementationChooserKt.shouldUseSplitTodo;

@ApiStatus.Internal
public final class ScopeBasedTodosPanel extends TodoPanel {

  private final Alarm myAlarm;
  private TodoScopeChooser myScopes;

  public ScopeBasedTodosPanel(@NotNull TodoView todoView,
                              @NotNull TodoPanelSettings settings,
                              @NotNull Content content) {
    super(todoView, settings, false, content);

    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    myScopes.addSelectionListener(() -> {
      rebuildWithAlarm(this.myAlarm);
      myTodoView.getState().selectedScope = myScopes.getSelectedScopeName();
    });
    rebuildWithAlarm(myAlarm);
  }

  @Override
  protected JComponent createCenterComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    final JComponent component = super.createCenterComponent();
    panel.add(component, BorderLayout.CENTER);
    String preselect = myTodoView.getState().selectedScope;
    myScopes = initScopeChooser(preselect);

    JPanel chooserPanel = new JPanel(new GridBagLayout());
    final JLabel scopesLabel = new JLabel(IdeBundle.message("label.scope"));
    scopesLabel.setLabelFor(myScopes.asComponent());
    final GridBagConstraints gc =
      new GridBagConstraints(GridBagConstraints.RELATIVE, 0, 1, 1, 0, 0, GridBagConstraints.WEST, GridBagConstraints.NONE,
                             JBUI.insets(2, 8, 2, 4), 0, 0);
    chooserPanel.add(scopesLabel, gc);
    gc.insets = JBUI.insets(2);
    chooserPanel.add(myScopes.asComponent(), gc);

    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1;
    chooserPanel.add(JBBox.createHorizontalBox(), gc);
    panel.add(chooserPanel, BorderLayout.NORTH);
    return panel;
  }

  @Override
  protected @NotNull TodoTreeBuilder createTreeBuilder(@NotNull JTree tree,
                                                       @NotNull Project project) {
    ScopeBasedTodosTreeBuilder builder = new ScopeBasedTodosTreeBuilder(tree, project, myScopes);
    builder.init();
    return builder;
  }

  private TodoScopeChooser initScopeChooser(String preselect) {
    if (shouldUseSplitTodo()) {
      FrontendScopeChooser scopeChooser = new FrontendScopeChooser(myProject, preselect, ScopesFilterConditionType.TODO);
      Disposer.register(this, scopeChooser);
      return new FrontendTodoScopeChooserImpl(scopeChooser);
    }
    else {
      ScopeChooserCombo schopeChooser = new ScopeChooserCombo(myProject, false, true, preselect);
      Disposer.register(this, schopeChooser);
      schopeChooser.setCurrentSelection(false);
      schopeChooser.setUsageView(false);
      return new TodoScopeChooserImpl(schopeChooser);
    }
  }
}