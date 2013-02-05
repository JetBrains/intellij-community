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

import com.intellij.AppTopics;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Arrays;

/**
 * @author cdr
*/
public class ReloadFileInOtherEncodingAction extends AnAction implements DumbAware {
  public ReloadFileInOtherEncodingAction() {
    this("Reload in...");
  }
  protected ReloadFileInOtherEncodingAction(String text) {
    super(text);
  }

  @Nullable("null means disabled, otherwise it's the document and the action description")
  public Pair<Document, String> checkEnabled(@NotNull VirtualFile virtualFile) {
    String failReason = ChooseFileEncodingAction.checkCanReload(virtualFile).second;
    if (failReason != null) return null;
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = documentManager.getDocument(virtualFile);
    if (document == null) return null;

    Charset charsetFromContent = EncodingManager.getInstance().getCachedCharsetFromContent(document);
    Charset charset = charsetFromContent != null ? charsetFromContent : virtualFile.getCharset();
    String text = MessageFormat.format("Reload ''{0}''-encoded file ''{1}'' in another encoding", charset.displayName(), virtualFile.getName());

    return Pair.create(document, text);
  }

  @Override
  public void update(AnActionEvent e) {
    VirtualFile myFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    Pair<Document, String> pair = myFile == null ? null : checkEnabled(myFile);
    e.getPresentation().setEnabled(pair != null);
    if (pair != null) {
      e.getPresentation().setDescription(pair.second);
    }
  }

  @Override
  public final void actionPerformed(final AnActionEvent e) {
    final VirtualFile myFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (myFile == null) return;
    Pair<Document, String> pair = checkEnabled(myFile);
    if (pair == null) return;
    final Document document = pair.first;
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);

    final byte[] bytes;
    try {
      bytes = myFile.contentsToByteArray();
    }
    catch (IOException e1) {
      return;
    }

    DefaultActionGroup group =
     new ChooseFileEncodingAction(myFile) {
      @Override
      public void update(final AnActionEvent e) {
      }

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        return createGroup(null, myFile.getCharset(), new Function<Charset, String>() {
          @Override
          public String fun(Charset charset) {
            return isCompatibleCharset(myFile, bytes, document.getText(), charset);
          }
        }); // no 'clear'
      }

      @Override
      protected void chosen(@Nullable VirtualFile virtualFile, @NotNull Charset charset) {
        if (virtualFile != null) {
          ReloadFileInOtherEncodingAction.this.chosen(document, editor, virtualFile, bytes, charset);
        }
      }
    }
    .createPopupActionGroup(null);

    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(getTemplatePresentation().getText(),
      group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    popup.showInBestPositionFor(e.getDataContext());
  }

  protected void chosen(@NotNull Document document,
                        Editor editor,
                        @NotNull VirtualFile virtualFile,
                        @NotNull byte[] bytes,
                        @NotNull final Charset charset) {
    if (!checkCompatibleEncodingAndWarn(virtualFile, bytes, document.getText(), charset, "Reload")) return;

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    //Project project = ProjectLocator.getInstance().guessProjectForFile(myFile);
    //if (documentManager.isFileModified(myFile)) {
    //  int result = Messages.showDialog(project, "File is modified. Reload file anyway?", "File is Modified", new String[]{"Reload", "Cancel"}, 0, AllIcons.General.WarningDialog);
    //  if (result != 0) return;
    //}

    Disposable disposable = Disposer.newDisposable();
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(disposable);
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      @Override
      public void beforeFileContentReload(VirtualFile file, @NotNull Document document) {
        EncodingManager.getInstance().setEncoding(file, charset);

        file.setCharset(charset);
        LoadTextUtil.setCharsetWasDetectedFromBytes(file, null);
      }
    });

    // if file was modified, the user will be asked here
    try {
      ((VirtualFileListener)documentManager).contentsChanged(
        new VirtualFileEvent(null, virtualFile, virtualFile.getName(), virtualFile.getParent()));
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  // returns true if user chose to continue anyway
  protected boolean checkCompatibleEncodingAndWarn(@NotNull VirtualFile virtualFile,
                                                   @NotNull byte[] bytes,
                                                   @NotNull String text,
                                                   @NotNull Charset charset,
                                                   @NotNull String action) {
    if (isCompatibleCharset(virtualFile, bytes, text, charset) == null) {
      int res = Messages.showDialog("File '"+virtualFile.getName()+"' most likely wasn't stored in the '" + charset.displayName() + "' encoding.",
                                    "Incompatible Encoding: " + charset.displayName(), new String[]{action + " anyway", "Cancel"}, 1,
                                    AllIcons.General.WarningDialog);
      if (res != 0) return false;
    }
    return true;
  }

  // check if file can be loaded in the encoding correctly:
  // returns true if bytes on disk, converted to text with the charset, converted back to bytes matched
  private static boolean canBeLoadedIn(@NotNull VirtualFile virtualFile, @NotNull byte[] bytes, @NotNull Charset charset) {
    String loaded = new String(bytes, charset);

    String separator = FileDocumentManager.getInstance().getLineSeparator(virtualFile, null);
    String toSave = StringUtil.convertLineSeparators(loaded, separator);
    byte[] bom = virtualFile.getBOM();
    bom = bom == null ? ArrayUtil.EMPTY_BYTE_ARRAY : bom;
    byte[] bytesToSave;
    try {
      bytesToSave = toSave.getBytes(charset);
    }
    catch (UnsupportedOperationException e) {
      return false;
    }
    if (!ArrayUtil.startsWith(bytesToSave, bom)) {
      bytesToSave = ArrayUtil.mergeArrays(bom, bytesToSave); // for 2-byte encodings String.getBytes(Charset) adds BOM automatically
    }

    return Arrays.equals(bytesToSave, bytes);
  }

  // charset filter
  @Nullable("null means incompatible")
  public String isCompatibleCharset(@NotNull VirtualFile virtualFile, @NotNull byte[] bytesOnDisk, @NotNull String text, @NotNull Charset charset) {
    return canBeLoadedIn(virtualFile, bytesOnDisk, charset) ? "Reload file '" + virtualFile.getName() + "' in '" + charset + "'" : null;
  }
}
