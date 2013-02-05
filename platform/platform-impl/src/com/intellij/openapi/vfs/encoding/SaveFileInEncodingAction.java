/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.encoding;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;

/**
 * @author cdr
*/
public class SaveFileInEncodingAction extends ReloadFileInOtherEncodingAction {
  public SaveFileInEncodingAction() {
    super("Save in...");
  }

  @Nullable
  @Override
  // document, description
  public Pair<Document, String> checkEnabled(@NotNull VirtualFile virtualFile) {
    String failReason = checkCanConvert(virtualFile);
    if (failReason != null) return null;
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = documentManager.getDocument(virtualFile);
    if (document == null) return null;

    Charset charsetFromContent = EncodingManager.getInstance().getCachedCharsetFromContent(document);
    Charset charset = charsetFromContent != null ? charsetFromContent : virtualFile.getCharset();
    String text = MessageFormat.format("Save ''{0}''-encoded file ''{1}'' in another encoding", charset.displayName(), virtualFile.getName());

    return Pair.create(document, text);
  }

  @Override
  @Nullable("null means incompatible")
  public String isCompatibleCharset(@NotNull VirtualFile virtualFile, @NotNull byte[] bytesOnDisk, @NotNull String text, @NotNull Charset charset) {
    return isSafeToConvertTo(virtualFile, text, charset) ? "Convert and save file '" + virtualFile.getName() + "' in '" + charset + "'" : null;
  }

  private static boolean isSafeToConvertTo(@NotNull VirtualFile virtualFile, @NotNull String text, @NotNull Charset charset) {
    try {
      Pair<Charset, byte[]> chosen = LoadTextUtil.chooseMostlyHarmlessCharset(virtualFile.getCharset(), charset, text);

      byte[] buffer = chosen.second;

      CharSequence textLoadedBack = LoadTextUtil.getTextByBinaryPresentation(buffer, charset);

      return text.equals(textLoadedBack.toString());
    }
    catch (UnsupportedOperationException e) { // unsupported encoding
      return false;
    }
  }

  @Override
  protected void chosen(@NotNull Document document,
                        Editor editor,
                        @NotNull VirtualFile virtualFile,
                        @NotNull byte[] bytes,
                        @NotNull final Charset charset) {
    if (!checkCompatibleEncodingAndWarn(virtualFile, bytes, document.getText(), charset, "Convert")) return;
    saveIn(document, editor, virtualFile, charset);
  }

  @Override
  protected boolean checkCompatibleEncodingAndWarn(@NotNull VirtualFile virtualFile,
                                                   @NotNull byte[] bytes,
                                                   @NotNull String text,
                                                   @NotNull Charset charset,
                                                   @NotNull String action) {
    if (isCompatibleCharset(virtualFile, bytes, text, charset) == null) {
      int res = Messages.showDialog("Encoding '" + charset.displayName() + "' does not support some characters from the text.",
                                    "Incompatible Encoding: " + charset.displayName(), new String[]{action + " anyway", "Cancel"}, 1,
                                    AllIcons.General.WarningDialog);
      if (res != 0) return false;
    }
    return true;
  }

  public static void saveIn(@NotNull Document document, Editor editor, @NotNull VirtualFile virtualFile, @NotNull Charset charset) {
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    documentManager.saveDocument(document);
    Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);
    boolean writable = project == null ? virtualFile.isWritable() : ReadonlyStatusHandler.ensureFilesWritable(project, virtualFile);
    if (!writable) {
      CommonRefactoringUtil
        .showErrorHint(project, editor, "Cannot save the file " + virtualFile.getPresentableUrl(), "Unable to Save", null);
      return;
    }

    virtualFile.setCharset(charset);
    try {
      LoadTextUtil.write(project, virtualFile, virtualFile, document.getText(), document.getModificationStamp());
    }
    catch (IOException io) {
      Messages.showErrorDialog(project, io.getMessage(), "Error Writing File");
    }

    EncodingManager.getInstance().setEncoding(virtualFile, charset);
  }

  @Nullable("null means enabled, notnull means disabled and contains error message")
  public static String checkCanConvert(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      return "file is a directory";
    }
    String reason = LoadTextUtil.wasCharsetDetectedFromBytes(virtualFile);
    if (reason == null) {
      return null;
    }
    String failReason = null;

    Charset charsetFromContent = ((EncodingManagerImpl)EncodingManager.getInstance()).computeCharsetFromContent(virtualFile);
    if (charsetFromContent != null) {
      failReason = "hard coded in text, encoding: {0}";
    }
    else {
      Pair<Charset, String> check = ChooseFileEncodingAction.checkFileType(virtualFile);
      if (check.second != null) {
        failReason = check.second;
      }
    }

    if (failReason != null) {
      return MessageFormat.format(failReason, charsetFromContent == null ? "" : charsetFromContent.displayName());
    }
    return null;
  }
}
