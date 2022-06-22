// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.find.FindBundle;
import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectExtension;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.PredefinedSearchScopeProviderImpl;
import com.intellij.psi.search.SearchScope;
import com.intellij.structuralsearch.Scopes;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.NullableConsumer;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.GridBagConstraintBuilder;
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
  private boolean myUpdating = false;

  final ActionToolbarImpl myToolbar;
  final JPanel myScopeDetailsPanel = new JPanel(new CardLayout());
  private final ModulesComboBox myModulesComboBox = new ModulesComboBox();
  private final DirectoryComboBoxWithButtons myDirectoryComboBox;
  private final ScopeChooserCombo myScopesComboBox = new ScopeChooserCombo();

  private String myCurrentNamedScope = null;

  public ScopePanel(@NotNull Project project, Disposable parent) {
    super(null);
    myProject = project;
    myScope = GlobalSearchScope.projectScope(myProject);
    myScopeType = Scopes.Type.PROJECT;

    final Module[] allModules = ModuleManager.getInstance(project).getModules();
    myModulesComboBox.setModules(Arrays.asList(allModules));
    if (allModules.length > 0) myModulesComboBox.setSelectedModule(allModules[0]);
    myModulesComboBox.addItemListener(e -> setScopeFromUI(Scopes.Type.MODULE, false));
    myModulesComboBox.setMinimumAndPreferredWidth(JBUIScale.scale(300));
    myScopesComboBox.initialize(project, true, false, "", SCOPE_FILTER).onSuccess(o -> {
      if (myCurrentNamedScope != null) {
        myScopesComboBox.selectItem(myCurrentNamedScope);
        myCurrentNamedScope = null;
      }
      myScopesComboBox.getComboBox().addItemListener(e -> setScopeFromUI(Scopes.Type.NAMED, false));
    });
    Disposer.register(parent, myScopesComboBox);
    myDirectoryComboBox = new DirectoryComboBoxWithButtons(myProject);
    myDirectoryComboBox.setCallback(() -> setScopeFromUI(Scopes.Type.DIRECTORY, false));

    myScopeDetailsPanel.add(Scopes.Type.PROJECT.toString(), new JLabel());
    myScopeDetailsPanel.add(Scopes.Type.MODULE.toString(), shrinkWrap(myModulesComboBox));
    myScopeDetailsPanel.add(Scopes.Type.DIRECTORY.toString(), myDirectoryComboBox);
    myScopeDetailsPanel.add(Scopes.Type.NAMED.toString(), shrinkWrap(myScopesComboBox));

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
    myToolbar.setTargetComponent(myToolbar);
    myToolbar.setForceMinimumSize(true);
    myToolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);

    final GridBagLayout layout = new GridBagLayout();
    final GridBagConstraintBuilder constraint = new GridBagConstraintBuilder();
    setLayout(layout);

    add(myToolbar, constraint.get());
    add(myScopeDetailsPanel, constraint.insets(0, UIUtil.DEFAULT_HGAP, 0, 0).growX().fillX().get());
  }

  private void selectNamedScope(String selectedScope) {
    myCurrentNamedScope = selectedScope;
    myScopesComboBox.selectItem(selectedScope);
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
    final Scopes.Type scopeType = Scopes.getType(selectedScope);
    myUpdating = true;
    try {
      if (selectedScope instanceof ModuleWithDependenciesScope) {
        final ModuleWithDependenciesScope scope = (ModuleWithDependenciesScope)selectedScope;
        myModulesComboBox.setItem(scope.getModule());
      }
      else if (selectedScope instanceof GlobalSearchScopesCore.DirectoryScope) {
        final GlobalSearchScopesCore.DirectoryScope directoryScope = (GlobalSearchScopesCore.DirectoryScope)selectedScope;
        myDirectoryComboBox.setDirectory(directoryScope.getDirectory());
        myDirectoryComboBox.setRecursive(directoryScope.isWithSubdirectories());
      }
      else if (selectedScope != null && scopeType == Scopes.Type.NAMED) {
        selectNamedScope(selectedScope.getDisplayName());
      }
    } finally {
      myUpdating = false;
    }
    showScope(scopeType);
  }

  public void setScopesFromContext(SearchScope scope) {
    DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
      Scopes.Type foundScope = null;
      myUpdating = true;
      try {
        boolean moduleFound = false;
        boolean directoryFound = false;
        boolean namedScopeFound = false;
        final Module[] modules = ModuleManager.getInstance(myProject).getModules();
        if (modules.length > 0) {
          // set some defaults
          final Module module = modules[0];
          myModulesComboBox.setSelectedModule(module);
          final VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
          if (roots.length > 0) {
            final VirtualFile root = roots[0];
            myDirectoryComboBox.setDirectory(root.isDirectory() ? root : root.getParent());
          }
        }

        final Module module = PlatformCoreDataKeys.MODULE.getData(context);
        if (module != null) {
          moduleFound = true;
          myModulesComboBox.setSelectedModule(module);
        }
        final Editor editor = CommonDataKeys.HOST_EDITOR.getData(context);
        if (editor != null) {
          final VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
          if (file != null) {
            myDirectoryComboBox.setDirectory(file.getParent());
          }
          selectNamedScope(PredefinedSearchScopeProviderImpl.getCurrentFileScopeName());
          setScope(scope);
          return;
        }

        final FindModel findModel = new FindModel();
        for (FindInProjectExtension extension : FindInProjectExtension.EP_NAME.getExtensionList()) {
          namedScopeFound = extension.initModelFromContext(findModel, context);
          if (namedScopeFound) {
            selectNamedScope(findModel.getCustomScopeName());
            foundScope = Scopes.Type.NAMED;
            break;
          }
        }

        final VirtualFile[] files = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(context);
        if (files != null && files.length > 0) {
          if (!moduleFound) {
            final Module fileModule = ModuleUtilCore.findModuleForFile(files[0], myProject);
            if (fileModule != null) {
              myModulesComboBox.setSelectedModule(fileModule);
            }
          }
          if (!namedScopeFound) {
            final SearchScope selectedFilesScope = PredefinedSearchScopeProviderImpl.getSelectedFilesScope(myProject, context, null);
            if (selectedFilesScope != null) {
              selectNamedScope(selectedFilesScope.getDisplayName());
            }
          }
          if (files.length == 1) {
            directoryFound = true;
            final VirtualFile file = files[0];
            myDirectoryComboBox.setDirectory(file.isDirectory() ? file : file.getParent());
            if (foundScope == null) {
              foundScope = Scopes.Type.DIRECTORY;
            }
          }
          else {
            for (VirtualFile file : files) {
              if (file.isDirectory()) {
                myDirectoryComboBox.setDirectory(file);
                directoryFound = true;
                break;
              }
            }
            if (foundScope == null) {
              foundScope = Scopes.Type.NAMED;
            }
          }
          if (!directoryFound) {
            final VirtualFile ancestor = VfsUtil.getCommonAncestor(List.of(files));
            if (ancestor != null) {
              myDirectoryComboBox.setDirectory(ancestor);
              directoryFound = true;
            }
          }
          if (!directoryFound) {
            final VirtualFile directory = files[0].getParent();
            if (directory != null) {
              myDirectoryComboBox.setDirectory(directory);
            }
          }
        }
      } finally {
        myUpdating = false;
      }
      showScope(foundScope == null ? Scopes.Type.PROJECT : foundScope);
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
    if (myUpdating) return;
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

  private void showScope(@NotNull Scopes.Type scopeType) {
    myScopeType = scopeType;
    ((CardLayout)myScopeDetailsPanel.getLayout()).show(myScopeDetailsPanel, scopeType.toString());
    setScopeFromUI(scopeType, true);
    myToolbar.updateActionsImmediately();
  }

  class ScopeToggleAction extends DumbAwareToggleAction {

    private final Scopes.Type myScopeType;

    ScopeToggleAction(@NotNull @NlsActions.ActionText String text, @NotNull Scopes.Type scopeType) {
      super(text);
      myScopeType = scopeType;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myScopeType == ScopePanel.this.myScopeType;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      showScope(myScopeType);
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }
  }
}
