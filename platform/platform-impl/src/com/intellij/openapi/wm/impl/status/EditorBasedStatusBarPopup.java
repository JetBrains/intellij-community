// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.DataManager;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
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
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.openapi.wm.CustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.ui.ClickListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.popup.PopupState;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.indexing.IndexingBundle;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

import static com.intellij.openapi.util.NlsContexts.StatusBarText;

public abstract class EditorBasedStatusBarPopup extends EditorBasedWidget implements StatusBarWidget.Multiframe, CustomStatusBarWidget {
  private final PopupState<JBPopup> myPopupState = PopupState.forPopup();
  private final JPanel myComponent;
  private final boolean myWriteableFileRequired;
  protected boolean actionEnabled;
  private final Alarm update;
  // store editor here to avoid expensive and EDT-only getSelectedEditor() retrievals
  private volatile Reference<Editor> myEditor = new WeakReference<>(null);

  public EditorBasedStatusBarPopup(@NotNull Project project, boolean writeableFileRequired) {
    super(project);
    myWriteableFileRequired = writeableFileRequired;
    update = new Alarm(this);
    myComponent = createComponent();
    myComponent.setVisible(false);

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        update();
        UIEventLogger.StatusBarPopupShown.log(project, EditorBasedStatusBarPopup.this.getClass());
        showPopup(e);
        return true;
      }
    }.installOn(myComponent, true);
    myComponent.setBorder(JBUI.CurrentTheme.StatusBar.Widget.border());
  }

  protected JPanel createComponent() {
    return new TextPanel.WithIconAndArrows();
  }

  @Override
  public final void selectionChanged(@NotNull FileEditorManagerEvent event) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    VirtualFile newFile = event.getNewFile();

    FileEditor fileEditor = newFile == null ? null : FileEditorManager.getInstance(getProject()).getSelectedEditor(newFile);
    Editor editor = fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;
    setEditor(editor);

    fileChanged(newFile);
  }

  @ApiStatus.Internal
  public final void setEditor(@Nullable Editor editor) {
    myEditor = new WeakReference<>(editor);
  }

  public final void selectionChanged(@Nullable VirtualFile newFile) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    FileEditor fileEditor = newFile == null ? null : FileEditorManager.getInstance(getProject()).getSelectedEditor(newFile);
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
  public final StatusBarWidget copy() {
    return createInstance(getProject());
  }

  @Nullable
  @Override
  public WidgetPresentation getPresentation() {
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
    if (myWriteableFileRequired) {
      ApplicationManager.getApplication().getMessageBus().connect(this)
        .subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(new VirtualFileListener() {
          @Override
          public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
            if (VirtualFile.PROP_WRITABLE.equals(event.getPropertyName())) {
              updateForFile(event.getFile());
            }
          }
        }));
    }
    setEditor(getEditor());
    update();
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
    if (!actionEnabled || myPopupState.isRecentlyHidden()) return; // do not show popup
    DataContext dataContext = getContext();
    ListPopup popup = createPopup(dataContext);

    if (popup != null) {
      Dimension dimension = popup.getContent().getPreferredSize();
      Point at = new Point(0, -dimension.height);
      myPopupState.prepareToShow(popup);
      popup.show(new RelativePoint(e.getComponent(), at));
      Disposer.register(this, popup); // destroy popup on unexpected project close
    }
  }

  @NotNull
  protected DataContext getContext() {
    Editor editor = getEditor();
    DataContext parent = DataManager.getInstance().getDataContext((Component)myStatusBar);
    VirtualFile selectedFile = getSelectedFile();
    return SimpleDataContext.builder()
      .add(CommonDataKeys.VIRTUAL_FILE, selectedFile)
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, selectedFile == null ? VirtualFile.EMPTY_ARRAY : new VirtualFile[] {selectedFile})
      .add(CommonDataKeys.PROJECT, getProject())
      .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, editor == null ? null : editor.getComponent())
      .setParent(parent)
      .build();
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  protected Alarm getUpdateAlarm() {
    return update;
  }

  protected boolean isEmpty() {
    Boolean result = ObjectUtils.doIfCast(myComponent, TextPanel.WithIconAndArrows.class,
                                          textPanel -> StringUtil.isEmpty(textPanel.getText()) && !textPanel.hasIcon());
    return result != null && result;
  }

  public boolean isActionEnabled() {
    return actionEnabled;
  }

  protected void updateComponent(@NotNull WidgetState state) {
    myComponent.setToolTipText(state.toolTip);
    ObjectUtils.consumeIfCast(
      myComponent, TextPanel.WithIconAndArrows.class,
      textPanel -> {
        textPanel.setTextAlignment(Component.CENTER_ALIGNMENT);
        textPanel.setIcon(state.icon);
        textPanel.setText(state.text);
      }
    );
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

      WidgetState state = SlowOperations.allowSlowOperations(() -> getWidgetState(file));
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

      actionEnabled = state.actionEnabled && isEnabledForFile(file);

      myComponent.setEnabled(actionEnabled);
      updateComponent(state);

      if (myStatusBar != null && !myComponent.isValid()) {
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

    protected final @Tooltip String toolTip;
    private final @StatusBarText String text;
    private final boolean actionEnabled;
    private Icon icon;

    private WidgetState() {
      this("", "", false);
    }

    public WidgetState(@Tooltip String toolTip, @StatusBarText String text, boolean actionEnabled) {
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
    public static WidgetState getDumbModeState(@Nls String name, @StatusBarText String widgetPrefix) {
      // todo: update accordingly to UX-252
      return new WidgetState(ActionUtil.getUnavailableMessage(name, false),
                             widgetPrefix + IndexingBundle.message("progress.indexing.updating"),
                             false);
    }

    public void setIcon(Icon icon) {
      this.icon = icon;
    }

    @Nls
    public String getText() {
      return text;
    }

    public boolean isActionEnabled() {
      return actionEnabled;
    }

    public @Tooltip String getToolTip() {
      return toolTip;
    }

    public Icon getIcon() {
      return icon;
    }
  }

  @NotNull
  protected abstract WidgetState getWidgetState(@Nullable VirtualFile file);

  /**
   * @param file result of {@link EditorBasedStatusBarPopup#getSelectedFile()}
   * @return false if widget should be disabled for {@code file}
   * even if {@link EditorBasedStatusBarPopup#getWidgetState(VirtualFile)} returned {@link WidgetState#actionEnabled}.
   */
  protected boolean isEnabledForFile(@Nullable VirtualFile file) {
    return file == null || !myWriteableFileRequired || file.isWritable();
  }

  @Nullable
  protected abstract ListPopup createPopup(DataContext context);

  protected void registerCustomListeners() {
  }

  @NotNull
  protected abstract StatusBarWidget createInstance(@NotNull Project project);
}