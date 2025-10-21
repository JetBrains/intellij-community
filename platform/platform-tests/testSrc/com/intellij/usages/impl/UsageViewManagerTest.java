// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.TemporaryDirectoryExtension;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.FixturesKt;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class UsageViewManagerTest {
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(null, "IJ"), OpenProjectTask.build(), false);
  private static final TestFixture<com.intellij.openapi.module.Module> moduleFixture = FixturesKt.moduleFixture(projectFixture, "shared", null);

  @RegisterExtension
  public final TemporaryDirectoryExtension temporaryDirectory = new TemporaryDirectoryExtension();

  @Test
  public void scopeCreatedForFindInDirectory() {
    VirtualFile dir = temporaryDirectory.createVirtualDir();
    FindModel findModel = new FindModel();
    findModel.setDirectoryName(dir.getPath());
    findModel.setWithSubdirectories(true);
    findModel.setProjectScope(false);
    UsageTarget target = new FindInProjectUtil.StringUsageTarget(projectFixture.get(), findModel);
    UsageViewManagerImpl manager = (UsageViewManagerImpl)UsageViewManager.getInstance(projectFixture.get());
    ApplicationManager.getApplication().runReadAction(() -> {
      SearchScope scope = manager.getMaxSearchScopeToWarnOfFallingOutOf(new UsageTarget[]{target}).get();
      GlobalSearchScope expectedScope = GlobalSearchScopesCore.directoryScope(
        projectFixture.get(),
        FindInProjectUtil.getDirectory(findModel),
        true
      );
      assertThat(scope).isEqualTo(expectedScope);
    });
  }

  @Test
  public void scopeCreatedForFindInModuleContent() {
    com.intellij.openapi.module.Module module = moduleFixture.get();

    FindModel findModel = new FindModel();
    findModel.setModuleName(module.getName());
    findModel.setProjectScope(false);
    UsageTarget target = new FindInProjectUtil.StringUsageTarget(projectFixture.get(), findModel);
    UsageViewManagerImpl manager = (UsageViewManagerImpl)UsageViewManager.getInstance(projectFixture.get());
    SearchScope scope = manager.getMaxSearchScopeToWarnOfFallingOutOf(new UsageTarget[]{target}).get();
    assertThat(module.getModuleContentScope()).isEqualTo(scope);
  }
}
