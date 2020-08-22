// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.ProjectRule;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class UsageViewManagerTest {
  @ClassRule
  public static final ProjectRule projectRule = new ProjectRule();

  @Rule
  public final TemporaryDirectory temporaryDirectory = new TemporaryDirectory();

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
      SearchScope scope = manager.getMaxSearchScopeToWarnOfFallingOutOf(new UsageTarget[]{target});
      assertThat(GlobalSearchScopesCore.directoryScope(projectRule.getProject(), dir, true)).isEqualTo(scope);
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
    SearchScope scope = manager.getMaxSearchScopeToWarnOfFallingOutOf(new UsageTarget[]{target});
    assertThat(module.getModuleContentScope()).isEqualTo(scope);
  }
}
