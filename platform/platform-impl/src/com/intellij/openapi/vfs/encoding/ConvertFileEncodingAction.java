/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;

/**
 * @author cdr
*/
public class ConvertFileEncodingAction extends ReloadFileInOtherEncodingAction {
  public ConvertFileEncodingAction() {
    text = "Convert to...";
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
  public boolean isCompatibleCharset(@NotNull VirtualFile virtualFile, @NotNull byte[] bytesOnDisk, @NotNull String text, @NotNull Charset charset) {
    return canConvertTo(virtualFile, text, charset);
  }

  private static boolean canConvertTo(@NotNull VirtualFile virtualFile, @NotNull String text, @NotNull Charset charset) {
    Pair<Charset, byte[]> chosen = LoadTextUtil.chooseMostlyHarmlessCharset(virtualFile.getCharset(), charset, text);

    byte[] buffer = chosen.second;

    CharSequence textLoadedBack = LoadTextUtil.getTextByBinaryPresentation(buffer, charset);

    return text.equals(textLoadedBack.toString());
  }

  @Override
  protected void chosen(@NotNull Document document,
                        Editor editor,
                        @NotNull VirtualFile virtualFile,
                        @NotNull byte[] bytes,
                        @NotNull final Charset charset) {
    if (!checkCompatibleEncodingAndWarn(virtualFile, bytes, document.getText(), charset, "Convert")) return;
    convert(document, editor, virtualFile, charset);
  }

  public static void convert(@NotNull Document document, Editor editor, @NotNull VirtualFile virtualFile, @NotNull Charset charset) {
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    if (documentManager.isFileModified(virtualFile)) {
      EncodingManager.getInstance().setEncoding(virtualFile, charset);

      LoadTextUtil.setCharsetWasDetectedFromBytes(virtualFile, null);

      documentManager.saveDocument(document);
    }
    else {
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

      ((VirtualFileListener)documentManager).contentsChanged(new VirtualFileEvent(null, virtualFile, virtualFile.getName(), virtualFile.getParent()));
    }
  }

  @Nullable("null means enabled, notnull means disabled and contains error message")
  private static String checkCanConvert(@NotNull VirtualFile virtualFile) {
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
