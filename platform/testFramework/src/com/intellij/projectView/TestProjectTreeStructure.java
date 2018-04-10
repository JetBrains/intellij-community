/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.projectView;

import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ProjectViewTestUtil;
import org.junit.Assert;

public class TestProjectTreeStructure extends AbstractProjectTreeStructure implements Disposable, ProjectViewSettings {
  private boolean myShowExcludedFiles = true;
  protected boolean myShowMembers = false;
  protected boolean myHideEmptyMiddlePackages;
  protected boolean myFlattenPackages;
  private boolean myFlattenModules;
  protected boolean myShowLibraryContents = true;

  public TestProjectTreeStructure(Project project, Disposable parentDisposable) {
    super(project);
    Disposer.register(parentDisposable, this);
  }

  public void checkNavigateFromSourceBehaviour(PsiElement element, VirtualFile virtualFile, AbstractProjectViewPSIPane pane) {
    Assert.assertNull(ProjectViewTestUtil.getNodeForElement(element, pane));
    pane.select(element, virtualFile, true);
    PlatformTestUtil.waitWhileBusy(pane.getTree());
    Assert.assertTrue(ProjectViewTestUtil.isExpanded(element, pane));
  }

  public AbstractProjectViewPSIPane createPane() {
    final AbstractProjectViewPSIPane pane = new TestProjectViewPSIPane(myProject, this, 9);
    pane.createComponent();
    Disposer.register(this, pane);
    PlatformTestUtil.waitWhileBusy(pane.getTree());
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
    return myShowLibraryContents;
  }

  @Override
  public boolean isShowExcludedFiles() {
    return myShowExcludedFiles;
  }

  @Override
  public boolean isShowModules() {
    return true;
  }

  public void setShowMembers(boolean showMembers) {
    myShowMembers = showMembers;
  }

  @Override
  public boolean isFlattenModules() {
    return myFlattenModules;
  }

  public void setFlattenModules(boolean flattenModules) {
    myFlattenModules = flattenModules;
  }

  public void setHideEmptyMiddlePackages(boolean hideEmptyMiddlePackages) {
    myHideEmptyMiddlePackages = hideEmptyMiddlePackages;
  }

  public void setFlattenPackages(boolean flattenPackages) {
    myFlattenPackages = flattenPackages;
  }

  public void hideExcludedFiles() {
    myShowExcludedFiles = false;
  }

  public void setShowLibraryContents(boolean showLibraryContents) {
    myShowLibraryContents = showLibraryContents;
  }

  @Override
  public void dispose() {
  }
}
