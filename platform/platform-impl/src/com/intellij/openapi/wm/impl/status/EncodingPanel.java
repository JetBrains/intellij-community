/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.encoding.ChooseFileEncodingAction;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.charset.Charset;

/**
 * @author cdr
 */
public class EncodingPanel extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget {
  private final TextPanel myComponent;
  private boolean actionEnabled;

  public EncodingPanel(@NotNull final Project project) {
    super(project);

    myComponent = new TextPanel(getMaxValue()){
      @Override
      protected void paintComponent(@NotNull final Graphics g) {
        super.paintComponent(g);
        if (actionEnabled && getText() != null) {
          final Rectangle r = getBounds();
          final Insets insets = getInsets();
          ARROWS_ICON.paintIcon(this, g, r.width - insets.right - ARROWS_ICON.getIconWidth() - 2, r.height / 2 - ARROWS_ICON.getIconHeight() / 2);
        }
      }
    };
    myComponent.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        update();
        showPopup(e);
      }
    });
    myComponent.setBorder(WidgetBorder.INSTANCE);
  }

  @Override
  public void selectionChanged(FileEditorManagerEvent event) {
    update();
  }

  @Override
  public void fileOpened(FileEditorManager source, VirtualFile file) {
    update();
  }

  @Override
  public StatusBarWidget copy() {
    return new EncodingPanel(getProject());
  }

  @NotNull
  public String ID() {
    return "Encoding";
  }

  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @NotNull
  private static String getMaxValue() {
    return "windows-1251";
  }

  private static final Icon ARROWS_ICON = IconLoader.getIcon("/ide/statusbar_arrows.png");
  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    // should update to reflect encoding-from-content
    EncodingManager.getInstance().addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(EncodingManagerImpl.PROP_CACHED_ENCODING_CHANGED)) {
          update();
        }
      }
    }, this);
    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileAdapter() {
      @Override
      public void propertyChanged(VirtualFilePropertyEvent event) {
        if (VirtualFile.PROP_ENCODING.equals(event.getPropertyName())) {
          update();
        }
      }
    }));
    final Alarm update = new Alarm();
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentAdapter() {
      @Override
      public void documentChanged(DocumentEvent e) {
        Document document = e.getDocument();
        Editor selectedEditor = getEditor();
        if (selectedEditor == null || selectedEditor.getDocument() != document) return;
        update.cancelAllRequests();
        update.addRequest(new Runnable() {
                              @Override
                              public void run() {
                                if (!isDisposed()) update();
                              }
                            }, 200);
      }
    }, this);
  }

  private void showPopup(MouseEvent e) {
    ListPopup popup = getPopupStep();
    if (popup == null) return;
    final Dimension dimension = popup.getContent().getPreferredSize();
    final Point at = new Point(0, -dimension.height);
    popup.show(new RelativePoint(e.getComponent(), at));
    Disposer.register(this, popup); // do not forget to destroy popup on unexpected project close
  }

  private ListPopup getPopupStep() {
    Pair<String,Boolean> result = ChooseFileEncodingAction.update(getSelectedFile());
    boolean enabled = result.second;
    final DataContext parent = DataManager.getInstance().getDataContext((Component)myStatusBar);
    final DataContext dataContext =
      SimpleDataContext.getSimpleContext(PlatformDataKeys.VIRTUAL_FILE.getName(), getSelectedFile(),
                                         SimpleDataContext.getSimpleContext(PlatformDataKeys.PROJECT.getName(), getProject(), parent));
    if (!enabled) {
      return null;
    }
    DefaultActionGroup group = new ChooseFileEncodingAction(getSelectedFile()) {
      @Override
      protected void chosen(VirtualFile virtualFile, Charset charset) {
        if (virtualFile != null) {
          EncodingManager.getInstance().setEncoding(virtualFile, charset);
          update(new AnActionEvent(null, dataContext, ActionPlaces.EDITOR_TOOLBAR, getTemplatePresentation(), ActionManager.getInstance(), 0));
          EncodingPanel.this.update();
        }
      }
    }.createGroup(false);
    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, dataContext, false, false, false, null, 30, null);
  }

  private void update() {
    final VirtualFile file = getSelectedFile();
    Pair<String, Boolean> result = ChooseFileEncodingAction.update(file);
    String text;
    String toolTip;
    if (file != null) {
      Charset charset = ChooseFileEncodingAction.cachedCharsetFromContent(file);
      if (charset == null) charset = file.getCharset();

      text = charset.displayName();
      actionEnabled = result.second;
      toolTip = result.first;
    }
    else {
      text = "";
      actionEnabled = false;
      toolTip = "";
    }
    myComponent.setToolTipText(toolTip);
    myComponent.setText(text);

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (actionEnabled) {
          myComponent.setForeground(UIUtil.getActiveTextColor());
        }
        else {
          myComponent.setForeground(UIUtil.getInactiveTextColor());
        }
      }
    });


    myStatusBar.updateWidget(ID());
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }
}
