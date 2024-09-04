// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.listeners.impl;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.listeners.RefactoringElementListenerProvider;
import com.intellij.refactoring.listeners.RefactoringListenerManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class RefactoringListenerManagerImpl extends RefactoringListenerManager {
  private final List<RefactoringElementListenerProvider> myListenerProviders = ContainerUtil.createLockFreeCopyOnWriteList();
  private final Project myProject;

  public RefactoringListenerManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void addListenerProvider(RefactoringElementListenerProvider provider) {
    myListenerProviders.add(provider);
  }

  public RefactoringTransaction startTransaction() {
    List<RefactoringElementListenerProvider> providers = new ArrayList<>(myListenerProviders);
    providers.addAll(RefactoringElementListenerProvider.EP_NAME.getExtensionList(myProject));
    return new RefactoringTransactionImpl(myProject, providers);
  }
}