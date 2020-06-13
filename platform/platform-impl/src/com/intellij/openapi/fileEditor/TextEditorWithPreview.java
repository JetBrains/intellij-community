// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBSplitter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashMap;
import java.util.Map;

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
  private JComponent myComponent;
  private SplitEditorToolbar myToolbarWrapper;
  private final String myName;
  public static final Key<Layout> DEFAULT_LAYOUT_FOR_FILE = Key.create("TextEditorWithPreview.DefaultLayout");

  public TextEditorWithPreview(@NotNull TextEditor editor,
                               @NotNull FileEditor preview,
                               @NotNull String editorName,
                               @NotNull Layout defaultLayout) {
    myEditor = editor;
    myPreview = preview;
    myName = editorName;
    myDefaultLayout = defaultLayout;
  }

  public TextEditorWithPreview(@NotNull TextEditor editor, @NotNull FileEditor preview, @NotNull String editorName) {
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
    if (myComponent == null) {
      final JBSplitter splitter = new JBSplitter(false, 0.5f, 0.15f, 0.85f);
      splitter.setSplitterProportionKey(getSplitterProportionKey());
      splitter.setFirstComponent(myEditor.getComponent());
      splitter.setSecondComponent(myPreview.getComponent());
      splitter.setDividerWidth(3);

      myToolbarWrapper = createMarkdownToolbarWrapper(splitter);

      if (myLayout == null) {
        String lastUsed = PropertiesComponent.getInstance().getValue(getLayoutPropertyName());
        myLayout = Layout.fromName(lastUsed, myDefaultLayout);
      }
      adjustEditorsVisibility();

      myComponent = JBUI.Panels.simplePanel(splitter).addToTop(myToolbarWrapper);
    }
    return myComponent;
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

  private void adjustEditorsVisibility() {
    myEditor.getComponent().setVisible(myLayout == Layout.SHOW_EDITOR || myLayout == Layout.SHOW_EDITOR_AND_PREVIEW);
    myPreview.getComponent().setVisible(myLayout == Layout.SHOW_PREVIEW || myLayout == Layout.SHOW_EDITOR_AND_PREVIEW);
  }

  private void invalidateLayout() {
    adjustEditorsVisibility();
    myToolbarWrapper.refresh();
    myComponent.repaint();

    final JComponent focusComponent = getPreferredFocusedComponent();
    if (focusComponent != null) {
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
        throw new IllegalStateException(myLayout.myName);
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

  public Layout getLayout() {
    return myLayout;
  }

  static class MyFileEditorState implements FileEditorState {
    private final Layout mySplitLayout;
    private final FileEditorState myFirstState;
    private final FileEditorState mySecondState;

    MyFileEditorState(Layout layout, FileEditorState firstState, FileEditorState secondState) {
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
    public boolean canBeMergedWith(FileEditorState otherState, FileEditorStateLevel level) {
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

  private class DoublingEventListenerDelegate implements PropertyChangeListener {
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
    SHOW_EDITOR("Editor only", AllIcons.General.LayoutEditorOnly),
    SHOW_PREVIEW("Preview only", AllIcons.General.LayoutPreviewOnly),
    SHOW_EDITOR_AND_PREVIEW("Editor and Preview", AllIcons.General.LayoutEditorPreview);

    private final String myName;
    private final Icon myIcon;

    Layout(String name, Icon icon) {
      myName = name;
      myIcon = icon;
    }

    public static Layout fromName(String name, Layout defaultValue) {
      for (Layout layout : Layout.values()) {
        if (layout.myName.equals(name)) {
          return layout;
        }
      }
      return defaultValue;
    }

    public String getName() {
      return myName;
    }

    public Icon getIcon() {
      return myIcon;
    }
  }

  private class ChangeViewModeAction extends ToggleAction implements DumbAware {
    private final Layout myActionLayout;

    ChangeViewModeAction(Layout layout) {
      super(layout.getName(), layout.getName(), layout.getIcon());
      myActionLayout = layout;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myLayout == myActionLayout;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state) {
        myLayout = myActionLayout;
        PropertiesComponent.getInstance().setValue(getLayoutPropertyName(), myLayout.myName, myDefaultLayout.myName);
        adjustEditorsVisibility();
      }
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
}