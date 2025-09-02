// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TestDataProvider implements DataProvider {
  private final Project myProject;
  private final boolean myWithRules;
  private final TestDataProvider myDelegateWithoutRules;

  public TestDataProvider(@NotNull Project project) {
    this(project, false);
  }

  private TestDataProvider(@NotNull Project project, boolean withRules) {
    myProject = project;
    myWithRules = withRules;
    if (myWithRules) {
      myDelegateWithoutRules = new TestDataProvider(project, false);
    }
    else {
      myDelegateWithoutRules = this;
    }
  }

  public static TestDataProvider withRules(Project project) {
    return new TestDataProvider(project, true);
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (myProject.isDisposed()) {
      throw new RuntimeException("TestDataProvider is already disposed for " + myProject + "\n" +
                                 "If you closed a project in test, please reset TestApplicationManager.setDataProvider.");
    }

    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(myProject);
    if (CommonDataKeys.EDITOR.is(dataId) || OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
      return manager.getSelectedTextEditor(true);
    }
    else if (PlatformCoreDataKeys.FILE_EDITOR.is(dataId)) {
      Editor editor = manager.getSelectedTextEditor();
      return editor == null ? null : TextEditorProvider.getInstance().getTextEditor(editor);
    }
    else {
      if (myWithRules) {
        return DataManager.getInstance().getCustomizedData(dataId, DataContext.EMPTY_CONTEXT, myDelegateWithoutRules);
      }
      return null;
    }
  }
}