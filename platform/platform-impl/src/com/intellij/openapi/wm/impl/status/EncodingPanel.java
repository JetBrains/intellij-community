// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.encoding.ChangeFileEncodingAction;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.vfs.encoding.EncodingManagerListener;
import com.intellij.openapi.vfs.encoding.EncodingUtil;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class EncodingPanel extends EditorBasedStatusBarPopup {
  public EncodingPanel(@NotNull Project project, @NotNull CoroutineScope scope) {
    super(project, false, scope);
  }

  @Override
  protected @NotNull WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null || file.isDirectory()) {
      return WidgetState.HIDDEN;
    }

    Pair<Charset, String> check = EncodingUtil.getCharsetAndTheReasonTooltip(file);
    String failReason = Pair.getSecond(check);
    Charset charset = ObjectUtils.notNull(Pair.getFirst(check), file.getCharset());
    String charsetName = ObjectUtils.notNull(charset.displayName(), IdeBundle.message("encoding.not.available"));
    String toolTipText = IdeBundle.message("status.bar.text.file.encoding", charsetName) + (failReason == null ? "" : " (" + failReason + ")");
    return new WidgetState(toolTipText, charsetName, failReason == null);
  }

  @Override
  protected @Nullable ListPopup createPopup(@NotNull DataContext context) {
    ChangeFileEncodingAction action = new ChangeFileEncodingAction();
    action.getTemplatePresentation().setText(IdeBundle.messagePointer("action.presentation.EncodingPanel.text"));
    return action.createPopup(context, (ActionGroup)ActionManager.getInstance().getAction("EncodingPanelActions"));
  }

  @Override
  protected void registerCustomListeners(@NotNull MessageBusConnection connection) {
    // should update to reflect encoding-from-content
    connection.subscribe(EncodingManagerListener.ENCODING_MANAGER_CHANGES, (document, propertyName, oldValue, newValue) -> {
      if (propertyName.equals(EncodingManagerImpl.PROP_CACHED_ENCODING_CHANGED)) {
        updateForDocument(document);
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileListener() {
        @Override
        public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
          if (VirtualFile.PROP_ENCODING.equals(event.getPropertyName())) {
            updateForFile(event.getFile());
          }
        }
      }));
  }

  @Override
  protected @NotNull StatusBarWidget createInstance(@NotNull Project project) {
    return new EncodingPanel(project, getScope());
  }

  @Override
  public @NotNull String ID() {
    return StatusBar.StandardWidgets.ENCODING_PANEL;
  }
}
