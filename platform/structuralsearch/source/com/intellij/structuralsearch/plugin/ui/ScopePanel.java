// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.find.FindBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.Scopes;
import com.intellij.ui.JBCardLayout;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Bas Leijdekkers
 */
public class ScopePanel extends JPanel {

  private final Project myProject;
  private SearchScope myScope;
  private Consumer<SearchScope> myCallback;
  Scopes.Type myScopeType;

  final ActionToolbarImpl myToolbar;
  final JPanel myScopeDetailsPanel = new JPanel(new JBCardLayout());
  private final ModulesComboBox myModulesComboBox = new ModulesComboBox();
  private final DirectoryComboBoxWithButtons myDirectoryComboBox;
  private final ScopeChooserCombo myScopesComboBox = new ScopeChooserCombo();

  public ScopePanel(@NotNull Project project) {
    super(null);
    myProject = project;

    final Module[] allModules = ModuleManager.getInstance(project).getModules();
    myModulesComboBox.setModules(Arrays.asList(allModules));
    if (allModules.length > 0) myModulesComboBox.setSelectedModule(allModules[0]);
    myModulesComboBox.addItemListener(e -> setScopeFromUI(Scopes.Type.MODULE));
    myScopesComboBox.init(project, true, false, "",
                          (ScopeDescriptor descriptor) -> IdeBundle.message("scope.class.hierarchy").equals(descriptor.getDisplay()) ||
                                                          !(descriptor.getScope() instanceof ModuleWithDependenciesScope));
    myScopesComboBox.getComboBox().addItemListener(e -> setScopeFromUI(Scopes.Type.NAMED));
    myDirectoryComboBox = new DirectoryComboBoxWithButtons(myProject);
    myDirectoryComboBox.setCallback((vFile, recursive) -> setScopeFromUI(Scopes.Type.DIRECTORY));

    myScopeDetailsPanel.add(Scopes.Type.PROJECT.toString(), new JLabel());
    myScopeDetailsPanel.add(Scopes.Type.MODULE.toString(), shrinkWrap(myModulesComboBox));
    myScopeDetailsPanel.add(Scopes.Type.DIRECTORY.toString(), myDirectoryComboBox);
    myScopeDetailsPanel.add(Scopes.Type.NAMED.toString(), shrinkWrap(myScopesComboBox));

    myScopeDetailsPanel.setBorder(JBUI.Borders.emptyBottom(UIUtil.isUnderDefaultMacTheme() ? 0 : 3));
    final DefaultActionGroup scopeActionGroup = new DefaultActionGroup(
      new ScopeToggleAction(FindBundle.message("find.popup.scope.project"), Scopes.Type.PROJECT),
      new ScopeToggleAction(FindBundle.message("find.popup.scope.module"), Scopes.Type.MODULE),
      new ScopeToggleAction(FindBundle.message("find.popup.scope.directory"), Scopes.Type.DIRECTORY),
      new ScopeToggleAction(FindBundle.message("find.popup.scope.scope"), Scopes.Type.NAMED)
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
            .addComponent(myScopeDetailsPanel)
    );
    layout.setVerticalGroup(
      layout.createParallelGroup()
            .addComponent(myToolbar)
            .addComponent(myScopeDetailsPanel)
    );
  }

  private static JComponent shrinkWrap(JComponent component) {
    final JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(component, BorderLayout.WEST);
    wrapper.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
    return wrapper;
  }

  public void setRecentDirectories(@NotNull List<String> recentDirectories) {
    myDirectoryComboBox.setRecentDirectories(recentDirectories);
  }

  public void setScope(SearchScope selectedScope) {
    myScope = (selectedScope == null) ? GlobalSearchScope.projectScope(myProject) : selectedScope;
    myScopeType = Scopes.getType(myScope);

    if (selectedScope instanceof ModuleWithDependenciesScope) {
      final ModuleWithDependenciesScope scope = (ModuleWithDependenciesScope)selectedScope;
      myModulesComboBox.setSelectedModule(scope.getModule());
    }
    else if (selectedScope instanceof GlobalSearchScopesCore.DirectoryScope) {
      final GlobalSearchScopesCore.DirectoryScope directoryScope = (GlobalSearchScopesCore.DirectoryScope)selectedScope;
      final VirtualFile directory = directoryScope.getDirectory();
      myDirectoryComboBox.setDirectory(directory);
      myDirectoryComboBox.setRecursive(directoryScope.isWithSubdirectories());
    }
    else if (selectedScope != null) {
      myScopesComboBox.init(myProject, true, false, selectedScope.getDisplayName());
    }
    myToolbar.updateActionsImmediately();
    ((JBCardLayout)myScopeDetailsPanel.getLayout()).show(myScopeDetailsPanel, myScopeType.toString());
  }

  public void setScopeCallback(Consumer<SearchScope> callback) {
    myCallback = callback;
  }

  public SearchScope getScope() {
    return myScope;
  }

  void setScopeFromUI(Scopes.Type type) {
    switch (type) {
      case PROJECT:
        myScope = GlobalSearchScope.projectScope(myProject);
        break;
      case MODULE:
        final Module module = myModulesComboBox.getSelectedModule();
        if (module == null) return;
        myScope = GlobalSearchScope.moduleScope(module);
        break;
      case DIRECTORY:
        final VirtualFile directory = myDirectoryComboBox.getDirectory();
        myScope = (directory == null) ? null : GlobalSearchScopesCore.directoryScope(myProject, directory, myDirectoryComboBox.isRecursive());
        break;
      case NAMED:
        myScope = myScopesComboBox.getSelectedScope();
        break;
    }
    if (myCallback != null) myCallback.accept(myScope);
  }

  class ScopeToggleAction extends ToggleAction {

    private final Scopes.Type myScopeType;

    public ScopeToggleAction(@Nullable String text, Scopes.Type scopeType) {
      super(text, null, EmptyIcon.ICON_0);
      myScopeType = scopeType;
      getTemplatePresentation().setDisabledIcon(EmptyIcon.ICON_0);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myScopeType == ScopePanel.this.myScopeType;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        ((JBCardLayout)myScopeDetailsPanel.getLayout()).swipe(myScopeDetailsPanel, myScopeType.toString(),
                                                              ScopePanel.this.myScopeType.compareTo(myScopeType) > 0 ? JBCardLayout.SwipeDirection.FORWARD : JBCardLayout.SwipeDirection.BACKWARD);
        ScopePanel.this.myScopeType = myScopeType;
        setScopeFromUI(myScopeType);
        myToolbar.updateActionsImmediately();
      }
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }
  }
}
