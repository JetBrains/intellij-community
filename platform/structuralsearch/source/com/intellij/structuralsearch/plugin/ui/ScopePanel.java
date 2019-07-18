// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.find.FindBundle;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.Scopes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.NullableConsumer;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class ScopePanel extends JPanel {

  private static final Condition<ScopeDescriptor> SCOPE_FILTER =
    (ScopeDescriptor descriptor) -> IdeBundle.message("scope.class.hierarchy").equals(descriptor.getDisplayName()) ||
                                    !(descriptor.getScope() instanceof ModuleWithDependenciesScope); // don't show module scope

  private final Project myProject;
  @NotNull private SearchScope myScope;
  private NullableConsumer<? super SearchScope> myConsumer;
  Scopes.Type myScopeType;

  final ActionToolbarImpl myToolbar;
  final JPanel myScopeDetailsPanel = new JPanel(new CardLayout());
  private final ModulesComboBox myModulesComboBox = new ModulesComboBox();
  private final DirectoryComboBoxWithButtons myDirectoryComboBox;
  private final ScopeChooserCombo myScopesComboBox = new ScopeChooserCombo();

  public ScopePanel(@NotNull Project project, Disposable parent) {
    super(null);
    myProject = project;
    myScope = GlobalSearchScope.projectScope(myProject);

    final Module[] allModules = ModuleManager.getInstance(project).getModules();
    myModulesComboBox.setModules(Arrays.asList(allModules));
    if (allModules.length > 0) myModulesComboBox.setSelectedModule(allModules[0]);
    myModulesComboBox.addItemListener(e -> setScopeFromUI(Scopes.Type.MODULE, false));
    myModulesComboBox.setMinimumAndPreferredWidth(JBUIScale.scale(300));
    myScopesComboBox.init(project, true, false, "", SCOPE_FILTER);
    myScopesComboBox.getComboBox().addItemListener(e -> setScopeFromUI(Scopes.Type.NAMED, false));
    Disposer.register(parent, myScopesComboBox);
    myDirectoryComboBox = new DirectoryComboBoxWithButtons(myProject);
    myDirectoryComboBox.setCallback(() -> setScopeFromUI(Scopes.Type.DIRECTORY, false));

    myScopeDetailsPanel.add(Scopes.Type.PROJECT.toString(), new JLabel());
    myScopeDetailsPanel.add(Scopes.Type.MODULE.toString(), shrinkWrap(myModulesComboBox));
    myScopeDetailsPanel.add(Scopes.Type.DIRECTORY.toString(), myDirectoryComboBox);
    myScopeDetailsPanel.add(Scopes.Type.NAMED.toString(), shrinkWrap(myScopesComboBox));

    myScopeDetailsPanel.setBorder(JBUI.Borders.emptyBottom(UIUtil.isUnderDefaultMacTheme() ? 0 : 3));
    final boolean fullVersion = !PlatformUtils.isDataGrip();
    final DefaultActionGroup scopeActionGroup =
      fullVersion
      ? new DefaultActionGroup(new ScopeToggleAction(FindBundle.message("find.popup.scope.project"), Scopes.Type.PROJECT),
                               new ScopeToggleAction(FindBundle.message("find.popup.scope.module"), Scopes.Type.MODULE),
                               new ScopeToggleAction(FindBundle.message("find.popup.scope.directory"), Scopes.Type.DIRECTORY),
                               new ScopeToggleAction(FindBundle.message("find.popup.scope.scope"), Scopes.Type.NAMED))
      : new DefaultActionGroup(new ScopeToggleAction(FindBundle.message("find.popup.scope.scope"), Scopes.Type.NAMED),
                               new ScopeToggleAction(FindBundle.message("find.popup.scope.directory"), Scopes.Type.DIRECTORY));
    myToolbar = (ActionToolbarImpl)ActionManager.getInstance().createActionToolbar("ScopePanel", scopeActionGroup, true);
    myToolbar.setForceMinimumSize(true);
    myToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    setScope(null);

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

  public void setScope(@Nullable SearchScope selectedScope) {
    if (selectedScope instanceof LocalSearchScope && selectedScope.getDisplayName().startsWith("Hierarchy of ")) {
      // don't restore Class Hierarchy scope
      selectedScope = null;
    }
    if (selectedScope != null) {
      myScope = selectedScope;
    }
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
    else if (selectedScope != null && selectedScope != GlobalSearchScope.projectScope(myProject)) {
      myScopesComboBox.selectItem(selectedScope.getDisplayName());
      // refresh scope, otherwise scope can be for example stale "Current File"
      final SearchScope scope = myScopesComboBox.getSelectedScope();
      myScope = scope == null ? GlobalSearchScope.projectScope(myProject) : scope;
    }
    myToolbar.updateActionsImmediately();
    ((CardLayout)myScopeDetailsPanel.getLayout()).show(myScopeDetailsPanel, myScopeType.toString());
  }

  public void setScopesFromContext() {
    DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
      final Module module = LangDataKeys.MODULE.getData(context);
      if (module != null) {
        myModulesComboBox.setSelectedModule(module);
      }
      final Editor editor = CommonDataKeys.HOST_EDITOR.getData(context);
      if (editor != null) {
        final Document document = editor.getDocument();
        final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file != null) {
          myDirectoryComboBox.setDirectory(file.getParent());
        }
        myScopesComboBox.selectItem(IdeBundle.message("scope.current.file"));
      }
      else {
        final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
        if (files != null && files.length > 0) {
          boolean found = false;
          for (VirtualFile file : files) {
            if (file.isDirectory()) {
              myDirectoryComboBox.setDirectory(file);
              found = true;
              break;
            }
          }
          if (!found) {
            myScopesComboBox.selectItem("Selected Files"); // this scope name is not available in a properties file
            myDirectoryComboBox.setDirectory(files[0].getParent());
          }
        }
      }
    });
  }

  public void setScopeConsumer(@Nullable NullableConsumer<? super SearchScope> consumer) {
    myConsumer = consumer;
  }

  @NotNull
  public SearchScope getScope() {
    return myScope;
  }

  void setScopeFromUI(@NotNull Scopes.Type type, boolean requestFocus) {
    switch (type) {
      case PROJECT:
        myScope = GlobalSearchScope.projectScope(myProject);
        break;
      case MODULE:
        final Module module = myModulesComboBox.getSelectedModule();
        if (module == null) return;
        myScope = GlobalSearchScope.moduleScope(module);
        if (requestFocus) myModulesComboBox.requestFocus();
        break;
      case DIRECTORY:
        final VirtualFile directory = myDirectoryComboBox.getDirectory();
        if (directory == null) return;
        myScope = GlobalSearchScopesCore.directoryScope(myProject, directory, myDirectoryComboBox.isRecursive());
        if (requestFocus) myDirectoryComboBox.getComboBox().requestFocus();
        break;
      case NAMED:
        final SearchScope scope = myScopesComboBox.getSelectedScope();
        myScope = scope == null ? GlobalSearchScope.projectScope(myProject) : scope;
        if (requestFocus) myScopesComboBox.requestFocus();
        break;
    }
    if (myConsumer != null) myConsumer.consume(myScope);
  }

  class ScopeToggleAction extends ToggleAction {

    private final Scopes.Type myScopeType;

    ScopeToggleAction(@NotNull String text, @NotNull Scopes.Type scopeType) {
      super(text, null, EmptyIcon.ICON_0);
      myScopeType = scopeType;
      getTemplatePresentation().setDisabledIcon(EmptyIcon.ICON_0);
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myScopeType == ScopePanel.this.myScopeType;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state) {
        ((CardLayout)myScopeDetailsPanel.getLayout()).show(myScopeDetailsPanel, myScopeType.toString());
        ScopePanel.this.myScopeType = myScopeType;
        setScopeFromUI(myScopeType, true);
        myToolbar.updateActionsImmediately();
      }
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }
  }
}
