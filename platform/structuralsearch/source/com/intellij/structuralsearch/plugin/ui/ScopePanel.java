// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.find.FindBundle;
import com.intellij.find.FindInProjectSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.ProjectScopeImpl;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * @author Bas Leijdekkers
 */
class ScopePanel extends JPanel {

  private final Project myProject;
  private SearchScope mySelectedScope;
  private Consumer<SearchScope> myCallback;

  private final ActionToolbarImpl myToolbar;
  private final JPanel myScopeDetailsPanel = new JPanel(new CardLayout());
  private final ModulesComboBox myModulesComboBox = new ModulesComboBox();
  private final DirectoryComboBoxWithButtons myDirectoryComboBox;
  private final ScopeChooserCombo myScopeChooserComboBox = new ScopeChooserCombo();

  public ScopePanel(@NotNull Project project, SearchScope selectedScope) {
    super(null);
    myProject = project;
    mySelectedScope = selectedScope;

    Module[] allModules = ModuleManager.getInstance(project).getModules();
    myModulesComboBox.setModules(Arrays.asList(allModules));
    if (allModules.length > 0) myModulesComboBox.setSelectedModule(allModules[0]);
    myScopeChooserComboBox.init(project, true, false, "",
                                descriptor -> IdeBundle.message("scope.class.hierarchy").equals(descriptor.getDisplay()) ||
                                              !(descriptor.getScope() instanceof ModuleWithDependenciesScope));
    myDirectoryComboBox = new DirectoryComboBoxWithButtons(myProject);
    myDirectoryComboBox.init(null, FindInProjectSettings.getInstance(myProject).getRecentDirectories()); // todo

    if (selectedScope instanceof ModuleWithDependenciesScope) {
      final ModuleWithDependenciesScope scope = (ModuleWithDependenciesScope)selectedScope;
      myModulesComboBox.setSelectedModule(scope.getModule());
    }
    else if (selectedScope instanceof GlobalSearchScopesCore.DirectoryScope) {
      //myDirectoryComboBox.setS;
    }
    else if (selectedScope != null) {
      myScopeChooserComboBox.init(project, true, false, selectedScope.getDisplayName());
    }

    myScopeDetailsPanel.add(ScopeType.PROJECT.toString(), new JLabel());
    myScopeDetailsPanel.add(ScopeType.MODULE.toString(), myModulesComboBox);
    myScopeDetailsPanel.add(ScopeType.DIRECTORY.toString(), myDirectoryComboBox);
    myScopeDetailsPanel.add(ScopeType.SCOPE.toString(), myScopeChooserComboBox);

    myScopeDetailsPanel.setBorder(JBUI.Borders.emptyBottom(UIUtil.isUnderDefaultMacTheme() ? 0 : 3));
    final DefaultActionGroup scopeActionGroup = new DefaultActionGroup(
      new ScopeToggleAction(FindBundle.message("find.popup.scope.project"), ScopeType.PROJECT),
      new ScopeToggleAction(FindBundle.message("find.popup.scope.module"), ScopeType.MODULE),
      new ScopeToggleAction(FindBundle.message("find.popup.scope.directory"), ScopeType.DIRECTORY),
      new ScopeToggleAction(FindBundle.message("find.popup.scope.scope"), ScopeType.SCOPE)
    );
    myToolbar = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar("ScopePanel", scopeActionGroup, true);
    myToolbar.setForceMinimumSize(true);
    myToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);

    final GroupLayout layout = new GroupLayout(this);
    setLayout(layout);
    layout.setHorizontalGroup(
      layout.createSequentialGroup()
            .addComponent(myToolbar, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED, 25, 25)
            .addComponent(myScopeDetailsPanel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup()
            .addComponent(myToolbar)
            .addComponent(myScopeDetailsPanel)
    );
  }

  public void setScopeCallback(Consumer<SearchScope> callback) {
    myCallback = callback;
  }

  public SearchScope getSelectedScope() {
    return mySelectedScope;
  }

  class ScopeToggleAction extends ToggleAction {

    private final ScopeType myScopeType;

    public ScopeToggleAction(@Nullable String text, ScopeType scopeType) {
      super(text, null, EmptyIcon.ICON_0);
      myScopeType = scopeType;
      getTemplatePresentation().setDisabledIcon(EmptyIcon.ICON_0);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myScopeType == ScopeType.fromScope(mySelectedScope);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        switch (myScopeType) {
          case PROJECT:
            mySelectedScope = GlobalSearchScope.projectScope(myProject);
            break;
          case MODULE:
            final Module module = myModulesComboBox.getSelectedModule();
            if (module != null) {
              mySelectedScope = GlobalSearchScope.moduleScope(module);
            }
            break;
          case DIRECTORY:
            final VirtualFile directory = myDirectoryComboBox.getDirectory();
            if (directory != null) {
              mySelectedScope = GlobalSearchScopesCore.directoryScope(myProject, directory, myDirectoryComboBox.isRecursive());
            }
            break;
          case SCOPE:
            mySelectedScope = myScopeChooserComboBox.getSelectedScope();
            break;
        }
        myToolbar.updateActionsImmediately();
        ((CardLayout)myScopeDetailsPanel.getLayout()).show(myScopeDetailsPanel, myScopeType.toString());
        if (myCallback != null) myCallback.accept(mySelectedScope);
      }
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }
  }

  enum ScopeType {
    PROJECT,
    MODULE,
    DIRECTORY,
    SCOPE;

    static ScopeType fromScope(SearchScope scope) {
      if (scope instanceof ProjectScopeImpl || scope == null) {
        return PROJECT;
      }
      else if (scope instanceof ModuleWithDependenciesScope) {
        return MODULE;
      }
      else if (scope instanceof GlobalSearchScopesCore.DirectoryScope) {
        return DIRECTORY;
      }
      else {
        return SCOPE;
      }
    }
  }
}
