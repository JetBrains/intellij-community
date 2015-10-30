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
package com.intellij.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.ProjectViewTestUtil;
import org.junit.Assert;

public class TestProjectTreeStructure extends AbstractProjectTreeStructure implements Disposable {
  protected boolean myShowMembers = false;
  protected boolean myHideEmptyMiddlePackages;
  protected boolean myFlattenPackages;

  public TestProjectTreeStructure(Project project, Disposable parentDisposable) {
    super(project);
    Disposer.register(parentDisposable, this);
  }

  public void checkNavigateFromSourceBehaviour(PsiElement element, VirtualFile virtualFile, AbstractProjectViewPSIPane pane) {
    Assert.assertNull(ProjectViewTestUtil.getNodeForElement(element, pane));
    pane.select(element, virtualFile, true);
    Assert.assertTrue(ProjectViewTestUtil.isExpanded(element, pane));
  }

  public AbstractProjectViewPSIPane createPane() {
    final AbstractProjectViewPSIPane pane = new TestProjectViewPSIPane(myProject, this, 9);
    pane.createComponent();
    Disposer.register(this, pane);
    return pane;
  }

  @Override
  public boolean isShowMembers() {
    return myShowMembers;
  }

  @Override
  public boolean isFlattenPackages() {
    return myFlattenPackages;
  }

  @Override
  public boolean isAbbreviatePackageNames() {
    return false;
  }

  @Override
  public boolean isHideEmptyMiddlePackages() {
    return myHideEmptyMiddlePackages;
  }

  @Override
  public boolean isShowLibraryContents() {
    return true;
  }

  @Override
  public boolean isShowModules() {
    return true;
  }

  public void setShowMembers(boolean showMembers) {
    myShowMembers = showMembers;
  }

  public void setHideEmptyMiddlePackages(boolean hideEmptyMiddlePackages) {
    myHideEmptyMiddlePackages = hideEmptyMiddlePackages;
  }

  public void setFlattenPackages(boolean flattenPackages) {
    myFlattenPackages = flattenPackages;
  }

  @Override
  public void dispose() {
  }
}
