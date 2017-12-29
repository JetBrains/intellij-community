/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
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
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;

/**
 * @author cdr
 */
public class EncodingPanel extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget {
  private final TextPanel myComponent;
  private boolean actionEnabled;
  private final Alarm update;
  // store editor here to avoid expensive and EDT-only getSelectedEditor() retrievals
  private volatile Reference<Editor> myEditor = new WeakReference<>(null);

  public EncodingPanel(@NotNull final Project project) {
    super(project);
    update = new Alarm(this);
    myComponent = new TextPanel.ExtraSize() {
      @Override
      protected void paintComponent(@NotNull final Graphics g) {
        super.paintComponent(g);
        if (actionEnabled && getText() != null) {
          final Rectangle r = getBounds();
          final Insets insets = getInsets();
          Icon arrows = AllIcons.Ide.Statusbar_arrows;
          arrows.paintIcon(this, g, r.width - insets.right - arrows.getIconWidth() - 2,
                           r.height / 2 - arrows.getIconHeight() / 2);
        }
      }
    };

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        update();
        showPopup(e);
        return true;
      }
    }.installOn(myComponent);
    myComponent.setBorder(WidgetBorder.WIDE);
  }

  @Override
  public void selectionChanged(@NotNull FileEditorManagerEvent event) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    VirtualFile newFile = event.getNewFile();
    fileChanged(newFile);
  }

  private void fileChanged(VirtualFile newFile) {
    FileEditor fileEditor = newFile == null ? null : FileEditorManager.getInstance(getProject()).getSelectedEditor(newFile);
    Editor editor = fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;
    myEditor = new WeakReference<>(editor);
    update();
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    fileChanged(file);
  }

  @Override
  public StatusBarWidget copy() {
    return new EncodingPanel(getProject());
  }

  @Override
  @NotNull
  public String ID() {
    return "Encoding";
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
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

    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(DocumentEvent e) {
        Document document = e.getDocument();
        updateForDocument(document);
      }
    }, this);
  }

  private void updateForDocument(@Nullable("null means update anyway") Document document) {
    Editor selectedEditor = myEditor.get();
    if (document != null && (selectedEditor == null || selectedEditor.getDocument() != document)) return;
    update();
  }

  private void updateForFile(@Nullable("null means update anyway") VirtualFile file) {
    if (file == null) {
      update();
    }
    else {
      updateForDocument(FileDocumentManager.getInstance().getCachedDocument(file));
    }
  }

  private void showPopup(@NotNull MouseEvent e) {
    if (!actionEnabled) {
      return;
    }
    DataContext dataContext = getContext();
    ListPopup popup = new ChangeFileEncodingAction().createPopup(dataContext);

    if (popup != null) {
      Dimension dimension = popup.getContent().getPreferredSize();
      Point at = new Point(0, -dimension.height);
      popup.show(new RelativePoint(e.getComponent(), at));
      Disposer.register(this, popup); // destroy popup on unexpected project close
    }
  }

  @NotNull
  private DataContext getContext() {
    Editor editor = getEditor();
    DataContext parent = DataManager.getInstance().getDataContext((Component)myStatusBar);
    return SimpleDataContext.getSimpleContext(
      ContainerUtil.<String, Object>immutableMapBuilder()
        .put(CommonDataKeys.VIRTUAL_FILE.getName(), getSelectedFile())
        .put(CommonDataKeys.PROJECT.getName(), getProject())
        .put(PlatformDataKeys.CONTEXT_COMPONENT.getName(), editor == null ? null : editor.getComponent())
        .build(),
      parent);
  }

  private void update() {
    if (update.isDisposed()) return;

    update.cancelAllRequests();
    update.addRequest(() -> {
      if (isDisposed()) return;

      VirtualFile file = getSelectedFile();
      actionEnabled = false;
      String charsetName;
      String toolTipText;

      if (file == null) {
        toolTipText = "";
        charsetName = "";
      }
      else {
        Pair<Charset, String> check = EncodingUtil.getCharsetAndTheReasonTooltip(file);
        String failReason = check == null ? null : check.second;
        actionEnabled = failReason == null;

        Charset charset = ObjectUtils.notNull(check == null ? null : check.first, file.getCharset());
        charsetName = ObjectUtils.notNull(charset.displayName(), "n/a");

        if (failReason == null) {
          toolTipText = "File Encoding: " + charsetName;

          myComponent.setForeground(UIUtil.getActiveTextColor());
          myComponent.setTextAlignment(Component.LEFT_ALIGNMENT);
        }
        else {
          toolTipText = StringUtil.capitalize(failReason) + ".";

          myComponent.setForeground(UIUtil.getInactiveTextColor());
          myComponent.setTextAlignment(Component.CENTER_ALIGNMENT);
        }
      }

      myComponent.setToolTipText(toolTipText);
      myComponent.setText(charsetName);

      if (myStatusBar != null) {
        myStatusBar.updateWidget(ID());
      }
    }, 200, ModalityState.any());
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
