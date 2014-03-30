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

  private Delegate myDelegate;

  private final ComboBox myComboBox;

  private DetailController myDetailController;
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

  public void setSelectesBreakpoint(Object breakpoint) {
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
      JLabel fake = new JLabel();
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

    ComboBoxModel model = new CollectionComboBoxModel(myBreakpointItems, breakpointItem);
    myComboBox = new ComboBox(model);

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
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
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
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setPropertiesPanel(@Nullable JPanel panel) {
      //To change body of implemented methods use File | Settings | File Templates.
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

    UserDataHolderBase myDataHolderBase = new UserDataHolderBase();

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
