/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author peter
 */
public class TestDataProvider implements DataProvider, DataContext {
  private final Project myProject;

  public TestDataProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (myProject.isDisposed()) {
      throw new RuntimeException("TestDataProvider is already disposed for " + myProject + "\n" +
                                 "If you closed a project in test, please reset IdeaTestApplication.setDataProvider.");
    }

    if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(myProject);
    if (manager == null) {
      return null;
    }
    if (CommonDataKeys.EDITOR.is(dataId) || OpenFileDescriptor.NAVIGATE_IN_EDITOR.is(dataId)) {
      return manager instanceof FileEditorManagerImpl ? ((FileEditorManagerImpl)manager).getSelectedTextEditor(true) : manager.getSelectedTextEditor();
    }
    else if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
      Editor editor = manager.getSelectedTextEditor();
      return editor == null ? null : TextEditorProvider.getInstance().getTextEditor(editor);
    }
    else {
      Editor editor = (Editor)getData(CommonDataKeys.EDITOR.getName());
      if (editor != null) {
        Object managerData = manager.getData(dataId, editor, editor.getCaretModel().getCurrentCaret());
        if (managerData != null) {
          return managerData;
        }
        JComponent component = editor.getContentComponent();
        if (component instanceof EditorComponentImpl) {
          return ((EditorComponentImpl)component).getData(dataId);
        }
      }
      return null;
    }
  }
}
