// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static com.intellij.openapi.actionSystem.ActionPlaces.TEXT_EDITOR_WITH_PREVIEW;

/**
 * Two panel editor with three states: Editor, Preview and Editor with Preview.
 * Based on SplitFileEditor by Valentin Fondaratov
 *
 * @author Konstantin Bulenkov
 */
public class TextEditorWithPreview extends UserDataHolderBase implements TextEditor {
  protected final TextEditor myEditor;
  protected final FileEditor myPreview;
  @NotNull
  private final MyListenersMultimap myListenersGenerator = new MyListenersMultimap();
  private final Layout myDefaultLayout;
  private Layout myLayout;
  private boolean myIsVerticalSplit;
  private JComponent myComponent;
  private JBSplitter mySplitter;
  private SplitEditorToolbar myToolbarWrapper;
  private final @Nls String myName;
  public static final Key<Layout> DEFAULT_LAYOUT_FOR_FILE = Key.create("TextEditorWithPreview.DefaultLayout");

  public TextEditorWithPreview(@NotNull TextEditor editor,
                               @NotNull FileEditor preview,
                               @NotNull @Nls String editorName,
                               @NotNull Layout defaultLayout,
                               boolean isVerticalSplit) {
    myEditor = editor;
    myPreview = preview;
    myName = editorName;
    myDefaultLayout = ObjectUtils.notNull(getLayoutForFile(myEditor.getFile()), defaultLayout);
    myIsVerticalSplit = isVerticalSplit;
  }

  public TextEditorWithPreview(@NotNull TextEditor editor,
                               @NotNull FileEditor preview,
                               @NotNull @Nls String editorName,
                               @NotNull Layout defaultLayout) {
    this(editor, preview, editorName, defaultLayout, false);
  }

  public TextEditorWithPreview(@NotNull TextEditor editor, @NotNull FileEditor preview, @NotNull @Nls String editorName) {
    this(editor, preview, editorName, Layout.SHOW_EDITOR_AND_PREVIEW);
  }

  public TextEditorWithPreview(@NotNull TextEditor editor, @NotNull FileEditor preview) {
    this(editor, preview, "TextEditorWithPreview");
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return myEditor.getBackgroundHighlighter();
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return myEditor.getCurrentLocation();
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return myEditor.getStructureViewBuilder();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myEditor);
    Disposer.dispose(myPreview);
  }

  @Override
  public void selectNotify() {
    myEditor.selectNotify();
    myPreview.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myEditor.deselectNotify();
    myPreview.deselectNotify();
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    if (myComponent != null) {
      return myComponent;
    }
    mySplitter = new JBSplitter(myIsVerticalSplit, 0.5f, 0.15f, 0.85f);
    mySplitter.setSplitterProportionKey(getSplitterProportionKey());
    mySplitter.setFirstComponent(myEditor.getComponent());
    mySplitter.setSecondComponent(myPreview.getComponent());
    mySplitter.setDividerWidth(2);

    myToolbarWrapper = createMarkdownToolbarWrapper(mySplitter);

    if (myLayout == null) {
      String lastUsed = PropertiesComponent.getInstance().getValue(getLayoutPropertyName());
      myLayout = Layout.fromId(lastUsed, myDefaultLayout);
    }
    adjustEditorsVisibility();

    BorderLayoutPanel panel = JBUI.Panels.simplePanel(mySplitter).addToTop(myToolbarWrapper);
    if (!isShowFloatingToolbar()) {
      myComponent = panel;
      return myComponent;
    }

    myToolbarWrapper.setVisible(false);
    MyEditorLayeredComponentWrapper layeredPane = new MyEditorLayeredComponentWrapper(panel);
    myComponent = layeredPane;
    LayoutActionsFloatingToolbar toolbar = new LayoutActionsFloatingToolbar(myComponent, new DefaultActionGroup(myToolbarWrapper.getRightToolbar().getActions()));
    Disposer.register(this, toolbar);
    layeredPane.add(panel, JLayeredPane.DEFAULT_LAYER);
    myComponent.add(toolbar, JLayeredPane.POPUP_LAYER);
    registerToolbarListeners(panel, toolbar);
    return myComponent;
  }

  protected boolean isShowFloatingToolbar() {
    return Registry.is("ide.text.editor.with.preview.show.floating.toolbar") && myToolbarWrapper.isLeftToolbarEmpty();
  }

  private void registerToolbarListeners(JComponent actualComponent, LayoutActionsFloatingToolbar toolbar) {
    UIUtil.addAwtListener(new MyMouseListener(toolbar), AWTEvent.MOUSE_MOTION_EVENT_MASK, toolbar);
    final var actualEditor = UIUtil.findComponentOfType(actualComponent, EditorComponentImpl.class);
    if (actualEditor != null) {
      final var editorKeyListener = new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent event) {
          toolbar.getVisibilityController().scheduleHide();
        }
      };
      actualEditor.getEditor().getContentComponent().addKeyListener(editorKeyListener);
      Disposer.register(toolbar, () -> {
        actualEditor.getEditor().getContentComponent().removeKeyListener(editorKeyListener);
      });
    }
  }

  public boolean isVerticalSplit() {
    return myIsVerticalSplit;
  }

  public void setVerticalSplit(boolean verticalSplit) {
    myIsVerticalSplit = verticalSplit;
    mySplitter.setOrientation(verticalSplit);
  }

  @NotNull
  private SplitEditorToolbar createMarkdownToolbarWrapper(@NotNull JComponent targetComponentForActions) {
    final ActionToolbar leftToolbar = createToolbar();
    if (leftToolbar != null) {
      leftToolbar.setTargetComponent(targetComponentForActions);
      leftToolbar.setReservePlaceAutoPopupIcon(false);
    }

    final ActionToolbar rightToolbar = createRightToolbar();
    rightToolbar.setTargetComponent(targetComponentForActions);
    rightToolbar.setReservePlaceAutoPopupIcon(false);

    return new SplitEditorToolbar(leftToolbar, rightToolbar);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    if (state instanceof MyFileEditorState) {
      final MyFileEditorState compositeState = (MyFileEditorState)state;
      if (compositeState.getFirstState() != null) {
        myEditor.setState(compositeState.getFirstState());
      }
      if (compositeState.getSecondState() != null) {
        myPreview.setState(compositeState.getSecondState());
      }
      if (compositeState.getSplitLayout() != null) {
        myLayout = compositeState.getSplitLayout();
        invalidateLayout();
      }
    }
  }

  protected void onLayoutChange(Layout oldValue, Layout newValue) { }

  private void adjustEditorsVisibility() {
    myEditor.getComponent().setVisible(myLayout == Layout.SHOW_EDITOR || myLayout == Layout.SHOW_EDITOR_AND_PREVIEW);
    myPreview.getComponent().setVisible(myLayout == Layout.SHOW_PREVIEW || myLayout == Layout.SHOW_EDITOR_AND_PREVIEW);
  }

  protected void setLayout(@NotNull Layout layout) {
    Layout oldLayout = myLayout;
    myLayout = layout;
    PropertiesComponent.getInstance().setValue(getLayoutPropertyName(), myLayout.myId, myDefaultLayout.myId);
    adjustEditorsVisibility();
    onLayoutChange(oldLayout, myLayout);
  }

  private void invalidateLayout() {
    adjustEditorsVisibility();
    myToolbarWrapper.refresh();
    myComponent.repaint();

    final JComponent focusComponent = getPreferredFocusedComponent();
    Component focusOwner = IdeFocusManager.findInstance().getFocusOwner();
    if (focusComponent != null && focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, getComponent())) {
      IdeFocusManager.findInstanceByComponent(focusComponent).requestFocus(focusComponent, true);
    }
  }

  @NotNull
  protected String getSplitterProportionKey() {
    return "TextEditorWithPreview.SplitterProportionKey";
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    switch (myLayout) {
      case SHOW_EDITOR_AND_PREVIEW:
      case SHOW_EDITOR:
        return myEditor.getPreferredFocusedComponent();
      case SHOW_PREVIEW:
        return myPreview.getPreferredFocusedComponent();
      default:
        throw new IllegalStateException(myLayout.myId);
    }
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return new MyFileEditorState(myLayout, myEditor.getState(level), myPreview.getState(level));
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myEditor.addPropertyChangeListener(listener);
    myPreview.addPropertyChangeListener(listener);

    final DoublingEventListenerDelegate delegate = myListenersGenerator.addListenerAndGetDelegate(listener);
    myEditor.addPropertyChangeListener(delegate);
    myPreview.addPropertyChangeListener(delegate);
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myEditor.removePropertyChangeListener(listener);
    myPreview.removePropertyChangeListener(listener);

    final DoublingEventListenerDelegate delegate = myListenersGenerator.removeListenerAndGetDelegate(listener);
    if (delegate != null) {
      myEditor.removePropertyChangeListener(delegate);
      myPreview.removePropertyChangeListener(delegate);
    }
  }

  @NotNull
  public TextEditor getTextEditor() {
    return myEditor;
  }

  @NotNull
  public FileEditor getPreviewEditor() {
    return myPreview;
  }

  public Layout getLayout() {
    return myLayout;
  }

  public static class MyFileEditorState implements FileEditorState {
    private final Layout mySplitLayout;
    private final FileEditorState myFirstState;
    private final FileEditorState mySecondState;

    public MyFileEditorState(Layout layout, FileEditorState firstState, FileEditorState secondState) {
      mySplitLayout = layout;
      myFirstState = firstState;
      mySecondState = secondState;
    }

    @Nullable
    public Layout getSplitLayout() {
      return mySplitLayout;
    }

    @Nullable
    public FileEditorState getFirstState() {
      return myFirstState;
    }

    @Nullable
    public FileEditorState getSecondState() {
      return mySecondState;
    }

    @Override
    public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
      return otherState instanceof MyFileEditorState
             && (myFirstState == null || myFirstState.canBeMergedWith(((MyFileEditorState)otherState).myFirstState, level))
             && (mySecondState == null || mySecondState.canBeMergedWith(((MyFileEditorState)otherState).mySecondState, level));
    }
  }

  @Override
  public boolean isModified() {
    return myEditor.isModified() || myPreview.isModified();
  }

  @Override
  public boolean isValid() {
    return myEditor.isValid() && myPreview.isValid();
  }

  private final class DoublingEventListenerDelegate implements PropertyChangeListener {
    @NotNull
    private final PropertyChangeListener myDelegate;

    private DoublingEventListenerDelegate(@NotNull PropertyChangeListener delegate) {
      myDelegate = delegate;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      myDelegate.propertyChange(
        new PropertyChangeEvent(TextEditorWithPreview.this, evt.getPropertyName(), evt.getOldValue(), evt.getNewValue()));
    }
  }

  private class MyListenersMultimap {
    private final Map<PropertyChangeListener, Pair<Integer, DoublingEventListenerDelegate>> myMap = new HashMap<>();

    @NotNull
    public DoublingEventListenerDelegate addListenerAndGetDelegate(@NotNull PropertyChangeListener listener) {
      if (!myMap.containsKey(listener)) {
        myMap.put(listener, Pair.create(1, new DoublingEventListenerDelegate(listener)));
      }
      else {
        final Pair<Integer, DoublingEventListenerDelegate> oldPair = myMap.get(listener);
        myMap.put(listener, Pair.create(oldPair.getFirst() + 1, oldPair.getSecond()));
      }

      return myMap.get(listener).getSecond();
    }

    @Nullable
    public DoublingEventListenerDelegate removeListenerAndGetDelegate(@NotNull PropertyChangeListener listener) {
      final Pair<Integer, DoublingEventListenerDelegate> oldPair = myMap.get(listener);
      if (oldPair == null) {
        return null;
      }

      if (oldPair.getFirst() == 1) {
        myMap.remove(listener);
      }
      else {
        myMap.put(listener, Pair.create(oldPair.getFirst() - 1, oldPair.getSecond()));
      }
      return oldPair.getSecond();
    }
  }

  @Nullable
  protected ActionToolbar createToolbar() {
    ActionGroup actionGroup = createLeftToolbarActionGroup();
    if (actionGroup != null) {
      return ActionManager.getInstance().createActionToolbar(TEXT_EDITOR_WITH_PREVIEW, actionGroup, true);
    }
    else {
      return null;
    }
  }

  @Nullable
  protected ActionGroup createLeftToolbarActionGroup() {
    return null;
  }

  @NotNull
  private ActionToolbar createRightToolbar() {
    final ActionGroup viewActions = createViewActionGroup();
    final ActionGroup group = createRightToolbarActionGroup();
    final ActionGroup rightToolbarActions = group == null
                                            ? viewActions
                                            : new DefaultActionGroup(group, Separator.create(), viewActions);
    return ActionManager.getInstance().createActionToolbar(TEXT_EDITOR_WITH_PREVIEW, rightToolbarActions, true);
  }

  @NotNull
  protected ActionGroup createViewActionGroup() {
    return new DefaultActionGroup(
      getShowEditorAction(),
      getShowEditorAndPreviewAction(),
      getShowPreviewAction()
    );
  }

  @Nullable
  protected ActionGroup createRightToolbarActionGroup() {
    return null;
  }

  @NotNull
  protected ToggleAction getShowEditorAction() {
    return new ChangeViewModeAction(Layout.SHOW_EDITOR);
  }

  @NotNull
  protected ToggleAction getShowPreviewAction() {
    return new ChangeViewModeAction(Layout.SHOW_PREVIEW);
  }

  @NotNull
  protected ToggleAction getShowEditorAndPreviewAction() {
    return new ChangeViewModeAction(Layout.SHOW_EDITOR_AND_PREVIEW);
  }

  public enum Layout {
    SHOW_EDITOR("Editor only", IdeBundle.messagePointer("tab.title.editor.only")),
    SHOW_PREVIEW("Preview only", IdeBundle.messagePointer("tab.title.preview.only")),
    SHOW_EDITOR_AND_PREVIEW("Editor and Preview", IdeBundle.messagePointer("tab.title.editor.and.preview"));

    private final @NotNull Supplier<@Nls String> myName;
    private final String myId;

    Layout(String id, @NotNull Supplier<String> name) {
      myId = id;
      myName = name;
    }

    public static Layout fromId(String id, Layout defaultValue) {
      for (Layout layout : values()) {
        if (layout.myId.equals(id)) {
          return layout;
        }
      }
      return defaultValue;
    }

    public @Nls String getName() {
      return myName.get();
    }

    public Icon getIcon(@Nullable TextEditorWithPreview editor) {
      if (this == SHOW_EDITOR) return AllIcons.General.LayoutEditorOnly;
      if (this == SHOW_PREVIEW) return AllIcons.General.LayoutPreviewOnly;
      return editor != null && editor.myIsVerticalSplit ? AllIcons.Actions.PreviewDetailsVertically : AllIcons.Actions.PreviewDetails;
    }
  }

  private class ChangeViewModeAction extends ToggleAction implements DumbAware {
    private final Layout myActionLayout;

    ChangeViewModeAction(Layout layout) {
      super(layout.getName(), layout.getName(), layout.getIcon(TextEditorWithPreview.this));
      myActionLayout = layout;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myLayout == myActionLayout;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state) {
        setLayout(myActionLayout);
      }
      else {
        if (myActionLayout == Layout.SHOW_EDITOR_AND_PREVIEW) {
          mySplitter.setOrientation(!myIsVerticalSplit);
          myIsVerticalSplit = !myIsVerticalSplit;
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setIcon(myActionLayout.getIcon(TextEditorWithPreview.this));
    }
  }

  @NotNull
  private String getLayoutPropertyName() {
    return myName + "Layout";
  }

  @Override
  public @Nullable VirtualFile getFile() {
    return getTextEditor().getFile();
  }

  @Override
  public @NotNull Editor getEditor() {
    return getTextEditor().getEditor();
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    return getTextEditor().canNavigateTo(navigatable);
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
    getTextEditor().navigateTo(navigatable);
  }

  protected void handleLayoutChange(boolean isVerticalSplit) {
    if (myIsVerticalSplit == isVerticalSplit) return;
    myIsVerticalSplit = isVerticalSplit;

    myToolbarWrapper.refresh();
    mySplitter.setOrientation(myIsVerticalSplit);
    myComponent.repaint();
  }

  @Nullable
  private static Layout getLayoutForFile(@Nullable VirtualFile file) {
    if (file != null) {
      return file.getUserData(DEFAULT_LAYOUT_FOR_FILE);
    }
    return null;
  }

  public static void openPreviewForFile(@NotNull Project project, @NotNull VirtualFile file) {
    file.putUserData(DEFAULT_LAYOUT_FOR_FILE, Layout.SHOW_PREVIEW);
    FileEditorManager.getInstance(project).openFile(file, true);
  }

  private static class MyEditorLayeredComponentWrapper extends JBLayeredPane {
    private final JComponent editorComponent;

    static final int toolbarTopPadding = 25;
    static final int toolbarRightPadding = 20;

    private MyEditorLayeredComponentWrapper(JComponent component) {
      editorComponent = component;
    }

    @Override
    public void doLayout() {
      final var components = getComponents();
      final var bounds = getBounds();
      for (Component component : components) {
        if (component == editorComponent) {
          component.setBounds(0, 0, bounds.width, bounds.height);
        }
        else {
          final var preferredComponentSize = component.getPreferredSize();
          var x = 0;
          var y = 0;
          if (component instanceof LayoutActionsFloatingToolbar) {
            x = bounds.width - preferredComponentSize.width - toolbarRightPadding;
            y = toolbarTopPadding;
          }
          component.setBounds(x, y, preferredComponentSize.width, preferredComponentSize.height);
        }
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return editorComponent.getPreferredSize();
    }
  }

  private class MyMouseListener implements AWTEventListener {
    private final LayoutActionsFloatingToolbar toolbar;
    private final Alarm alarm;

    MyMouseListener(LayoutActionsFloatingToolbar toolbar) {
      this.toolbar = toolbar;
      alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, toolbar);
    }

    @Override
    public void eventDispatched(AWTEvent event) {
      var isMouseOutsideToolbar = toolbar.getMousePosition() == null;
      if (myComponent.getMousePosition() != null) {
        alarm.cancelAllRequests();
        toolbar.getVisibilityController().scheduleShow();
        if (isMouseOutsideToolbar) {
          alarm.addRequest(() -> {
            toolbar.getVisibilityController().scheduleHide();
          }, 1400);
        }
      }
      else if (isMouseOutsideToolbar) {
        toolbar.getVisibilityController().scheduleHide();
      }
    }
  }
}