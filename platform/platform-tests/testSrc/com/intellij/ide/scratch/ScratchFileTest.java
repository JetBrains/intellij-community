// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.scratch;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorOpenTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

public class ScratchFileTest extends LightPlatformCodeInsightTestCase {

  private PsiFile createXmlScratch(String text) {
    ScratchFileCreationHelper.Context context = new ScratchFileCreationHelper.Context();
    context.language = XMLLanguage.INSTANCE;
    context.text = text;
    context.caretOffset = text.length();
    return ScratchFileCreator.doCreateNewScratch(getProject(), context);
  }

  public void testXmlCompletion() {
    PsiFile file = createXmlScratch("<");
    EditorOpenTestUtil.awaitPendingEditorOpen(getProject());
    Editor editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
    assertNotNull(editor);
    assertSame(file.getVirtualFile(), FileDocumentManager.getInstance().getFile(editor.getDocument()));
    assertEquals(1, editor.getCaretModel().getOffset());
    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(getProject(), editor);
  }

  public void testIsFileOfTypeWorks() {
    WriteAction.run(() -> {
      assertTrue(FileTypeManager.getInstance().isFileOfType(createXmlScratch("").getVirtualFile(), StdFileTypes.XML));
    });
    EditorOpenTestUtil.awaitPendingEditorOpen(getProject());
  }

  public void testCreateScratchOrTempFileReturnsScratch() {
    VirtualFile file = ScratchFileCreator.createScratchOrTempFile(getProject(), "report.txt", PlainTextLanguage.INSTANCE, "text");
    assertNotNull(file);
    assertTrue(ScratchUtil.isInScratchesRoot(file));
  }
}
