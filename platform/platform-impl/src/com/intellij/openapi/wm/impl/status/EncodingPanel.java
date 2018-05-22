// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.encoding.ChangeFileEncodingAction;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.vfs.encoding.EncodingUtil;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

public class EncodingPanel extends EditorBasedStatusBarPopup {
  public EncodingPanel(@NotNull Project project) {
    super(project);
  }

  @NotNull
  @Override
  protected WidgetState getWidgetState(@Nullable VirtualFile file) {
    if (file == null) {
      return WidgetState.HIDDEN;
    }

    Pair<Charset, String> check = EncodingUtil.getCharsetAndTheReasonTooltip(file);
    String failReason = check == null ? null : check.second;
    Charset charset = ObjectUtils.notNull(check == null ? null : check.first, file.getCharset());
    String charsetName = ObjectUtils.notNull(charset.displayName(), "n/a");
    String toolTipText = failReason == null ? "File Encoding: " + charsetName : StringUtil.capitalize(failReason) + ".";
    return new WidgetState(toolTipText, charsetName, failReason == null);
  }

  @Nullable
  @Override
  protected ListPopup createPopup(DataContext context) {
    return new ChangeFileEncodingAction().createPopup(context);
  }

  @Override
  protected void registerCustomListeners() {
    // should update to reflect encoding-from-content
    EncodingManager.getInstance().addPropertyChangeListener(evt -> {
      if (evt.getPropertyName().equals(EncodingManagerImpl.PROP_CACHED_ENCODING_CHANGED)) {
        Document document = evt.getSource() instanceof Document ? (Document)evt.getSource() : null;
        updateForDocument(document);
      }
    }, this);
    ApplicationManager.getApplication().getMessageBus().connect(this)
      .subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileListener() {
        @Override
        public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
          if (VirtualFile.PROP_ENCODING.equals(event.getPropertyName())) {
            updateForFile(event.getFile());
          }
        }
      }));
  }

  @NotNull
  @Override
  protected StatusBarWidget createInstance(Project project) {
    return new EncodingPanel(project);
  }

  @Override
  @NotNull
  public String ID() {
    return StatusBar.StandardWidgets.ENCODING_PANEL;
  }
}
