/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.find.FindModel;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;

public class UsageViewManagerTest extends PlatformTestCase {

  public void testScopeCreatedForFindInDirectory() {
    VirtualFile dir = getProject().getBaseDir();
    FindModel findModel = new FindModel();
    findModel.setDirectoryName(dir.getPath());
    findModel.setWithSubdirectories(true);
    findModel.setProjectScope(false);
    UsageTarget target = new FindInProjectUtil.StringUsageTarget(getProject(), findModel);
    UsageViewManagerImpl manager = (UsageViewManagerImpl)UsageViewManager.getInstance(getProject());
    SearchScope scope = manager.getMaxSearchScopeToWarnOfFallingOutOf(new UsageTarget[]{target});
    assertEquals(scope, GlobalSearchScopesCore.directoryScope(getProject(), dir, true));
  }

  public void testScopeCreatedForFindInModuleContent() {
    FindModel findModel = new FindModel();
    findModel.setModuleName(getModule().getName());
    findModel.setProjectScope(false);
    UsageTarget target = new FindInProjectUtil.StringUsageTarget(getProject(), findModel);
    UsageViewManagerImpl manager = (UsageViewManagerImpl)UsageViewManager.getInstance(getProject());
    SearchScope scope = manager.getMaxSearchScopeToWarnOfFallingOutOf(new UsageTarget[]{target});
    assertEquals(scope, getModule().getModuleContentScope());
  }
}
