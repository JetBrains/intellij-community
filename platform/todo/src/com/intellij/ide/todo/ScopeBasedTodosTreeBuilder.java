// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo;

import com.intellij.ide.todo.scopeChooser.TodoScopeChooser;
import com.intellij.ide.todo.model.TodoScope;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JTree;

@ApiStatus.Internal
public final class ScopeBasedTodosTreeBuilder extends TodoTreeBuilder {

  private final @NotNull TodoScopeChooser myScopes;

  public ScopeBasedTodosTreeBuilder(@NotNull JTree tree,
                                    @NotNull Project project,
                                    @NotNull TodoScopeChooser scopes) {
    super(tree, project);
    myScopes = scopes;
  }

  @Override
  public @Nullable TodoScope getScope() {
    String scopeId = myScopes.getSelectedScopeId();
    if (scopeId == null) return null;
    return new TodoScope.NamedScope(scopeId);
  }

  @Override
  protected @NotNull TodoTreeStructure createTreeStructure() {
    return new ScopeBasedTodosTreeStructure(myProject, myScopes);
  }
}