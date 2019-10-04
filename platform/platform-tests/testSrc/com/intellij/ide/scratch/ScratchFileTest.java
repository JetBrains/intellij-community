// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class ScratchFileTest extends LightPlatformCodeInsightTestCase {

  public void testXmlCompletion() {
    ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
    context.language = XMLLanguage.INSTANCE;
    PsiFile file = ScratchFileActions.doCreateNewScratch(getProject(), context);
    assertNotNull(file);
    Document document = FileDocumentManager.getInstance().getDocument(file.getVirtualFile());
    document.setText("<");
    Editor editor = ((TextEditor)FileEditorManager.getInstance(getProject()).openFile(file.getVirtualFile(), true)[0]).getEditor();
    editor.getCaretModel().moveToOffset(1);
    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(getProject(), editor);
  }

  @Override
  protected boolean isRunInWriteAction() {
    return true;
  }
}
