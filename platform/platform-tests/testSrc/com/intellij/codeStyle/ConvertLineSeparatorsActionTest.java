// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeStyle;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ConvertLineSeparatorsActionTest extends HeavyPlatformTestCase {
  public void testLF2CRLF() throws IOException {
    checkConvert("a\nb", new ConvertToWindowsLineSeparatorsAction(), "a\r\nb");
  }
  public void testCRLF2LF() throws IOException {
    checkConvert("a\r\nb", new ConvertToUnixLineSeparatorsAction(), "a\nb");
  }
  public void testCR2LF() throws IOException {
    checkConvert("a\rb", new ConvertToUnixLineSeparatorsAction(), "a\nb");
  }
  public void testCRLF2CR() throws IOException {
    checkConvert("a\r\nb", new ConvertToMacLineSeparatorsAction(), "a\rb");
  }

  @NotNull
  private VirtualFile checkConvert(@NotNull String oldContent, @NotNull AbstractConvertLineSeparatorsAction action, @NotNull String expectedContent) throws IOException {
    VirtualFile vf = createTempFile("txt", null, oldContent, CharsetToolkit.UTF8_CHARSET);
    DataContext context = SimpleDataContext
      .getSimpleContext(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName(), new VirtualFile[]{vf}, SimpleDataContext.getProjectContext(getProject()));
    action.actionPerformed(AnActionEvent.createFromDataContext("", null, context));
    String newContent = FileUtil.loadFile(new File(vf.getPath()));
    assertEquals(expectedContent, newContent);
    return vf;
  }

  public void testChangeLineSeparatorOnEmptyFileShouldPersist() throws IOException {
    VirtualFile vf = createTempFile("txt", null, "", CharsetToolkit.UTF8_CHARSET);
    String separator = FileDocumentManager.getInstance().getLineSeparator(vf, getProject());
    AbstractConvertLineSeparatorsAction action = separator.equals("\n") ? new ConvertToWindowsLineSeparatorsAction() : new ConvertToUnixLineSeparatorsAction();

    VirtualFile file = checkConvert("", action, "");
    String newSeparator = FileDocumentManager.getInstance().getLineSeparator(file, getProject());
    String expectedSeparator = separator.equals("\n") ? "\r\n" : "\n";
    assertEquals(expectedSeparator, newSeparator);
    Document document = FileDocumentManager.getInstance().getDocument(file);

    WriteAction.run(() -> document.setText("a\nb"));
    FileDocumentManager.getInstance().saveAllDocuments();
    assertEquals("a\nb", LoadTextUtil.loadText(file).toString());
    assertEquals(StringUtil.toHexString(("a" + expectedSeparator + "b").getBytes(StandardCharsets.UTF_8)), StringUtil.toHexString(file.contentsToByteArray()));
  }
}
