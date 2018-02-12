// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.popup.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

public class BreakpointChooser {

  private DetailView myDetailViewDelegate;

  private final Delegate myDelegate;

  private final ComboBox<BreakpointItem> myComboBox;

  private final DetailController myDetailController;
  private final List<BreakpointItem> myBreakpointItems;
  private BreakpointChooser.MyDetailView myDetailView;

  public void setDetailView(DetailView detailView) {
    myDetailViewDelegate = detailView;
    myDetailView = new MyDetailView(myDetailViewDelegate.getEditorState());
    myDetailController.setDetailView(myDetailView);
  }

  public Object getSelectedBreakpoint() {
    return ((BreakpointItem)myComboBox.getSelectedItem()).getBreakpoint();
  }

  private void pop(DetailView.PreviewEditorState pushed) {
    if (pushed.getFile() != null) {
      myDetailViewDelegate
        .navigateInPreviewEditor(
          new DetailView.PreviewEditorState(pushed.getFile(), pushed.getNavigate(), pushed.getAttributes()));
    }
    else {
      myDetailViewDelegate.clearEditor();
    }
  }

  public void setSelectedBreakpoint(Object breakpoint) {
    myComboBox.setSelectedItem(findItem(breakpoint, myBreakpointItems));
  }

  public interface Delegate {
    void breakpointChosen(Project project, BreakpointItem breakpointItem);
  }

  public BreakpointChooser(final Project project, Delegate delegate, Object baseBreakpoint, List<BreakpointItem> breakpointItems) {
    myDelegate = delegate;
    myBreakpointItems = breakpointItems;

    BreakpointItem breakpointItem = findItem(baseBreakpoint, myBreakpointItems);
    final Ref<Object> hackedSelection = Ref.create();

    myDetailController = new DetailController(new MasterController() {
      final JLabel fake = new JLabel();
      @Override
      public ItemWrapper[] getSelectedItems() {
        if (hackedSelection.get() == null) {
          return new ItemWrapper[0];
        }
        return new ItemWrapper[]{((BreakpointItem) hackedSelection.get())};
      }

      @Override
      public JLabel getPathLabel() {
        return fake;
      }
    });

    ComboBoxModel<BreakpointItem> model = new CollectionComboBoxModel<>(myBreakpointItems, breakpointItem);
    myComboBox = new ComboBox<>(model);

    myComboBox.addPopupMenuListener(new PopupMenuListener() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
        if (myDetailView != null) {
          myDetailView.clearEditor();
        }
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e) {
        if (myDetailView != null) {
          myDetailView.clearEditor();
        }
      }
    });
    myComboBox.setRenderer(new ItemWrapperListRenderer(project, null) {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        super.customizeCellRenderer(list, value, index, selected, hasFocus);
        if (selected) {
          if (hackedSelection.get() != value) {
            hackedSelection.set(value);
            myDetailController.updateDetailView();
          }
        }
      }
    });

    myComboBox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent event) {
        myDelegate.breakpointChosen(project, ((BreakpointItem)myComboBox.getSelectedItem()));
      }
    });
  }

  @Nullable
  private static BreakpointItem findItem(Object baseBreakpoint, List<BreakpointItem> breakpointItems) {
    BreakpointItem breakpointItem = null;
    for (BreakpointItem item : breakpointItems) {
      if (item.getBreakpoint() == baseBreakpoint) {
        breakpointItem = item;
        break;
      }
    }
    return breakpointItem;
  }

  public JComponent getComponent() {
    return myComboBox;
  }

  private class MyDetailView implements DetailView {

    private final PreviewEditorState myPushed;
    private ItemWrapper myCurrentItem;

    public MyDetailView(PreviewEditorState pushed) {
      myPushed = pushed;
      putUserData(BreakpointItem.EDITOR_ONLY, Boolean.TRUE);
    }

    @Override
    public Editor getEditor() {
      return myDetailViewDelegate.getEditor();
    }

    @Override
    public void navigateInPreviewEditor(PreviewEditorState editorState) {
      if (myDetailViewDelegate != null) {
        myDetailViewDelegate.navigateInPreviewEditor(editorState);
      }
    }

    @Override
    public JPanel getPropertiesPanel() {
      return null;
    }

    @Override
    public void setPropertiesPanel(@Nullable JPanel panel) {

    }

    @Override
    public void clearEditor() {
      pop(myPushed);
    }

    @Override
    public PreviewEditorState getEditorState() {
      return myDetailViewDelegate.getEditorState();
    }

    public void setCurrentItem(ItemWrapper currentItem) {
      myCurrentItem = currentItem;
    }

    @Override
    public ItemWrapper getCurrentItem() {
      return myCurrentItem;
    }

    @Override
    public boolean hasEditorOnly() {
      return true;
    }

    final UserDataHolderBase myDataHolderBase = new UserDataHolderBase();

    @Override
    public <T> T getUserData(@NotNull Key<T> key) {
      return myDataHolderBase.getUserData(key);
    }

    @Override
    public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
      myDataHolderBase.putUserData(key, value);
    }
  }
}
