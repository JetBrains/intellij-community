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
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.Charset;
import java.text.MessageFormat;

/**
 * @author cdr
*/
public class ReloadFileInOtherEncodingAction extends AnAction implements DumbAware, Condition<Charset> {
  protected VirtualFile myFile;
  protected String text;

  public ReloadFileInOtherEncodingAction() {
    text = "Reload in...";
  }

  @Nullable("null means disabled, otherwise it's the document and the action description")
  protected Pair<Document, String> checkEnabled(@NotNull VirtualFile virtualFile) {
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
    myFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    Pair<Document, String> pair = myFile == null ? null : checkEnabled(myFile);
    e.getPresentation().setEnabled(pair != null);
    if (pair != null) {
      e.getPresentation().setDescription(pair.second);
      e.getPresentation().setText(text);
    }
  }

  @Override
  public final void actionPerformed(final AnActionEvent e) {
    Pair<Document, String> pair = checkEnabled(myFile);
    if (pair == null) return;
    final Document document = pair.first;
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);

    DefaultActionGroup group =
     new ChooseFileEncodingAction(myFile) {
      @Override
      public void update(final AnActionEvent e) {
      }

      @NotNull
      @Override
      protected DefaultActionGroup createPopupActionGroup(JComponent button) {
        return createGroup(null, ReloadFileInOtherEncodingAction.this, "Reload file ''{0}'' in''{1}''", myFile.getCharset()); // no 'clear'
      }

      @Override
      protected void chosen(@Nullable VirtualFile virtualFile, @NotNull Charset charset) {
        if (virtualFile != null) {
          ReloadFileInOtherEncodingAction.this.chosen(document, editor, virtualFile, charset);
        }
      }
    }
    .createPopupActionGroup(null);

    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      text, group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
    popup.showInBestPositionFor(e.getDataContext());
  }

  protected void chosen(@NotNull Document document, Editor editor, @NotNull VirtualFile virtualFile, @NotNull final Charset charset) {
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
        EncodingManager.getInstance().setEncoding(myFile, charset);

        myFile.setCharset(charset);
        LoadTextUtil.setCharsetWasDetectedFromBytes(myFile, null);
      }
    });

    // if file was modified, the user will be asked here
    try {
      ((VirtualFileListener)documentManager).contentsChanged(new VirtualFileEvent(null, myFile, myFile.getName(), myFile.getParent()));
    }
    finally {
      Disposer.dispose(disposable);
    }
  }

  // charset filter
  @Override
  public boolean value(Charset charset) {
    return true;
  }
}
