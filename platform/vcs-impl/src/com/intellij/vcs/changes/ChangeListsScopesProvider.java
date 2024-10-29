// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public final class ChangeListsScopesProvider extends CustomScopesProviderEx {
  private final @NotNull Project myProject;

  public static ChangeListsScopesProvider getInstance(Project project) {
    return CUSTOM_SCOPES_PROVIDER.findExtension(ChangeListsScopesProvider.class, project);
  }

  public ChangeListsScopesProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull List<NamedScope> getCustomScopes() {
    if (myProject.isDefault() || !ProjectLevelVcsManager.getInstance(myProject).hasActiveVcss()) {
      return Collections.emptyList();
    }

    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);

    List<NamedScope> result = new ArrayList<>();
    result.add(new ChangeListScope(changeListManager));

    if (ChangesUtil.hasMeaningfulChangelists(myProject)) {
      List<LocalChangeList> changeLists = changeListManager.getChangeLists();
      boolean skipSingleDefaultCL = Registry.is("vcs.skip.single.default.changelist") &&
                                    changeLists.size() == 1 && changeLists.get(0).isBlank();
      if (!skipSingleDefaultCL) {
        for (ChangeList list : changeLists) {
          result.add(new ChangeListScope(changeListManager, list.getName()));
        }
      }
    }
    return result;
  }

  @Override
  public NamedScope getCustomScope(@NotNull String name) {
    if (myProject.isDefault()) return null;
    final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    if (ChangeListScope.ALL_CHANGED_FILES_SCOPE_NAME.equals(name)) {
      return new ChangeListScope(changeListManager);
    }
    if (ChangesUtil.hasMeaningfulChangelists(myProject)) {
      final LocalChangeList changeList = changeListManager.findChangeList(name);
      if (changeList != null) {
        return new ChangeListScope(changeListManager, changeList.getName());
      }
    }
    return null;
  }

  @Override
  public boolean isVetoed(NamedScope scope, ScopePlace place) {
    if (place == ScopePlace.SETTING) {
      if (myProject.isDefault()) return false;
      final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      return changeListManager.findChangeList(scope.getScopeId()) != null;
    }
    return false;
  }
}
