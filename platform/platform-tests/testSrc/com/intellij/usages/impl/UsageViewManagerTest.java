// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.ProjectExtension;
import com.intellij.testFramework.TemporaryDirectoryExtension;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class UsageViewManagerTest {
  @RegisterExtension
  public static final ProjectExtension projectRule = new ProjectExtension();

  @RegisterExtension
  public final TemporaryDirectoryExtension temporaryDirectory = new TemporaryDirectoryExtension();

  @Test
  public void scopeCreatedForFindInDirectory() {
    VirtualFile dir = temporaryDirectory.createVirtualDir();
    FindModel findModel = new FindModel();
    findModel.setDirectoryName(dir.getPath());
    findModel.setWithSubdirectories(true);
    findModel.setProjectScope(false);
    UsageTarget target = new FindInProjectUtil.StringUsageTarget(projectRule.getProject(), findModel);
    UsageViewManagerImpl manager = (UsageViewManagerImpl)UsageViewManager.getInstance(projectRule.getProject());
    ApplicationManager.getApplication().runReadAction(() -> {
      SearchScope scope = manager.getMaxSearchScopeToWarnOfFallingOutOf(new UsageTarget[]{target}).get();
      GlobalSearchScope expectedScope = GlobalSearchScopesCore.directoryScope(
        projectRule.getProject(),
        FindInProjectUtil.getDirectory(findModel),
        true
      );
      assertThat(scope).isEqualTo(expectedScope);
    });
  }

  @Test
  public void scopeCreatedForFindInModuleContent() {
    Module module = projectRule.getModule();

    FindModel findModel = new FindModel();
    findModel.setModuleName(module.getName());
    findModel.setProjectScope(false);
    UsageTarget target = new FindInProjectUtil.StringUsageTarget(projectRule.getProject(), findModel);
    UsageViewManagerImpl manager = (UsageViewManagerImpl)UsageViewManager.getInstance(projectRule.getProject());
    SearchScope scope = manager.getMaxSearchScopeToWarnOfFallingOutOf(new UsageTarget[]{target}).get();
    assertThat(module.getModuleContentScope()).isEqualTo(scope);
  }
}
