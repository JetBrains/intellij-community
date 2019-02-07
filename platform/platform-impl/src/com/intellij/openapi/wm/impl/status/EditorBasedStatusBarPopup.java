// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public abstract class EditorBasedStatusBarPopup extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget {
  private final TextPanel.WithIconAndArrows myComponent;
  private boolean actionEnabled;
  private final Alarm update;
  // store editor here to avoid expensive and EDT-only getSelectedEditor() retrievals
  private volatile Reference<Editor> myEditor = new WeakReference<>(null);

  public EditorBasedStatusBarPopup(@NotNull Project project) {
    super(project);
    update = new Alarm(this);
    myComponent = new TextPanel.WithIconAndArrows() {
      @Override
      protected boolean shouldPaintArrows() {
        return actionEnabled;
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

    Project project = getProject();
    assert project != null;
    FileEditor fileEditor = newFile == null ? null : FileEditorManager.getInstance(project).getSelectedEditor(newFile);
    Editor editor = fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;
    myEditor = new WeakReference<>(editor);

    fileChanged(newFile);
  }

  private void fileChanged(VirtualFile newFile) {
    handleFileChange(newFile);
    update();
  }

  protected void handleFileChange(VirtualFile file) {
  }

  @Override
  public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    fileChanged(file);
  }

  @Override
  public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
    fileChanged(file);
  }

  @Override
  public StatusBarWidget copy() {
    return createInstance(getProject());
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    super.install(statusBar);
    registerCustomListeners();
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent e) {
        Document document = e.getDocument();
        updateForDocument(document);
      }
    }, this);
  }

  protected void updateForDocument(@Nullable("null means update anyway") Document document) {
    Editor selectedEditor = myEditor.get();
    if (document != null && (selectedEditor == null || selectedEditor.getDocument() != document)) return;
    update();
  }

  protected void updateForFile(@Nullable("null means update anyway") VirtualFile file) {
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
    ListPopup popup = createPopup(dataContext);

    if (popup != null) {
      Dimension dimension = popup.getContent().getPreferredSize();
      Point at = new Point(0, -dimension.height);
      popup.show(new RelativePoint(e.getComponent(), at));
      Disposer.register(this, popup); // destroy popup on unexpected project close
    }
  }

  @NotNull
  protected DataContext getContext() {
    Editor editor = getEditor();
    DataContext parent = DataManager.getInstance().getDataContext((Component)myStatusBar);
    VirtualFile selectedFile = getSelectedFile();
    return SimpleDataContext.getSimpleContext(
      ContainerUtil.<String, Object>immutableMapBuilder()
        .put(CommonDataKeys.VIRTUAL_FILE.getName(), selectedFile)
        .put(CommonDataKeys.VIRTUAL_FILE_ARRAY.getName(), selectedFile == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[] {selectedFile})
        .put(CommonDataKeys.PROJECT.getName(), getProject())
        .put(PlatformDataKeys.CONTEXT_COMPONENT.getName(), editor == null ? null : editor.getComponent())
        .build(),
      parent);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  protected boolean isEmpty() {
    return StringUtil.isEmpty(myComponent.getText()) && !myComponent.hasIcon();
  }

  public boolean isActionEnabled() {
    return actionEnabled;
  }

  @TestOnly
  public void updateInTests(boolean immediately) {
    update();
    update.drainRequestsInTest();
    UIUtil.dispatchAllInvocationEvents();
    if (immediately) {
      // for widgets with background activities, the first flush() adds handlers to be called
      update.drainRequestsInTest();
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  @TestOnly
  public void flushUpdateInTests() {
    update.drainRequestsInTest();
  }

  public void update() {
    update(null);
  }

  public void update(@Nullable Runnable finishUpdate) {
    if (update.isDisposed()) return;

    update.cancelAllRequests();
    update.addRequest(() -> {
      if (isDisposed()) return;

      VirtualFile file = getSelectedFile();

      WidgetState state = getWidgetState(file);
      if (state == WidgetState.NO_CHANGE) {
        return;
      }

      if (state == WidgetState.NO_CHANGE_MAKE_VISIBLE) {
        myComponent.setVisible(true);
        return;
      }

      if (state == WidgetState.HIDDEN) {
        myComponent.setVisible(false);
        return;
      }

      myComponent.setVisible(true);

      actionEnabled = state.actionEnabled && file != null && (!requiresWritableFile() || file.isWritable());

      String widgetText = state.text;
      String toolTipText = state.toolTip;
      if (actionEnabled) {
        myComponent.setForeground(UIUtil.getActiveTextColor());
        myComponent.setTextAlignment(Component.LEFT_ALIGNMENT);
      }
      else {
        myComponent.setForeground(UIUtil.getInactiveTextColor());
        myComponent.setTextAlignment(Component.CENTER_ALIGNMENT);
      }

      myComponent.setIcon(state.icon);
      myComponent.setToolTipText(toolTipText);
      myComponent.setText(widgetText);
      myComponent.invalidate();

      if (myStatusBar != null) {
        myStatusBar.updateWidget(ID());
      }

      if (finishUpdate != null) {
        finishUpdate.run();
      }
      afterVisibleUpdate(state);
    }, 200, ModalityState.any());
  }

  protected void afterVisibleUpdate(@NotNull WidgetState state) {}

  protected static class WidgetState {
    /**
     * Return this state if you want to hide the widget
     */
    public static final WidgetState HIDDEN = new WidgetState();

    /**
     * Return this state if you don't want to change widget presentation
     */
    public static final WidgetState NO_CHANGE = new WidgetState();

    /**
     * Return this state if you want to show widget in its previous state
     * but without updating its content
     */
    public static final WidgetState NO_CHANGE_MAKE_VISIBLE = new WidgetState();

    protected final String toolTip;
    private final String text;
    private final boolean actionEnabled;
    private Icon icon;

    private WidgetState() {
      this("", "", false);
    }

    public WidgetState(String toolTip, String text, boolean actionEnabled) {
      this.toolTip = toolTip;
      this.text = text;
      this.actionEnabled = actionEnabled;
    }

    /**
     * Returns a special state for dumb mode (when indexes are not ready).
     * Your widget should show this state if it depends on indexes, when DumbService.isDumb is true.
     *
     * Use myConnection.subscribe(DumbService.DUMB_MODE, your_listener) inside registerCustomListeners,
     *   and call update() inside listener callbacks, to refresh your widget state when indexes are loaded
     */
    public static WidgetState getDumbModeState(String name, String widgetPrefix) {
      // todo: update accordingly to UX-252
      return new WidgetState(ActionUtil.getUnavailableMessage(name, false), widgetPrefix + IdeBundle.message("progress.indexing.updating"), false);
    }

    public void setIcon(Icon icon) {
      this.icon = icon;
    }
  }

  protected boolean requiresWritableFile() {
    return true;
  }

  @NotNull
  protected abstract WidgetState getWidgetState(@Nullable VirtualFile file);

  @Nullable
  protected abstract ListPopup createPopup(DataContext context);

  protected abstract void registerCustomListeners();

  @NotNull
  protected abstract StatusBarWidget createInstance(Project project);
}
