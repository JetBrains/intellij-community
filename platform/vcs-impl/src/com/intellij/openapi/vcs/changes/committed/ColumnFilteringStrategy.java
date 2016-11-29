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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.*;

/**
 * @author yole
 */
public class ColumnFilteringStrategy implements ChangeListFilteringStrategy {
  private final JScrollPane myScrollPane;
  private final JList myValueList;
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final ChangeListColumn myColumn;
  private final Class<? extends CommittedChangesProvider> myProviderClass;
  private final MyListModel myModel;
  private final CommittedChangeListToStringConvertor ourConvertorInstance = new CommittedChangeListToStringConvertor();

  private Object[] myPrefferedSelection;

  public ColumnFilteringStrategy(final ChangeListColumn column,
                                 final Class<? extends CommittedChangesProvider> providerClass) {
    myModel = new MyListModel();
    myValueList = new JBList();
    myScrollPane = ScrollPaneFactory.createScrollPane(myValueList);
    myValueList.setModel(myModel);
    myValueList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        for (ChangeListener listener : myListeners) {
          listener.stateChanged(new ChangeEvent(this));
        }
      }
    });
    myValueList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (index == 0) {
          append(value.toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else if (value.toString().length() == 0) {
          append(VcsBundle.message("committed.changes.filter.none"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
    myColumn = column;
    myProviderClass = providerClass;
  }

  @Override
  public CommittedChangesFilterKey getKey() {
    return new CommittedChangesFilterKey(toString(), CommittedChangesFilterPriority.USER);
  }

  public String toString() {
    return myColumn.getTitle();
  }

  @Nullable
  public JComponent getFilterUI() {
    return myScrollPane;
  }

  public void setFilterBase(List<CommittedChangeList> changeLists) {
    myPrefferedSelection = null;
    appendFilterBase(changeLists);
  }

  public void addChangeListener(final ChangeListener listener) {
    myListeners.add(listener);
  }

  public void removeChangeListener(final ChangeListener listener) {
    myListeners.remove(listener);
  }

  public void resetFilterBase() {
    myPrefferedSelection = myValueList.getSelectedValues();
    myValueList.clearSelection();
    myModel.clear();
    myValueList.revalidate();
    myValueList.repaint();
  }

  public void appendFilterBase(List<CommittedChangeList> changeLists) {
    final Object[] oldSelection = myModel.isEmpty() ? myPrefferedSelection : myValueList.getSelectedValues();

    myModel.addNext(changeLists, ourConvertorInstance);
    if (oldSelection != null) {
      for (Object o : oldSelection) {
        myValueList.setSelectedValue(o, false);
      }
    }
    myValueList.revalidate();
    myValueList.repaint();
  }

  private class CommittedChangeListToStringConvertor implements Convertor<CommittedChangeList, String> {
    public String convert(CommittedChangeList o) {
      if (myProviderClass == null || myProviderClass.isInstance(o.getVcs().getCommittedChangesProvider())) {
        return myColumn.getValue(ReceivedChangeList.unwrap(o)).toString();
      }
      return null;
    }
  }

  @NotNull
  public List<CommittedChangeList> filterChangeLists(List<CommittedChangeList> changeLists) {
    final Object[] selection = myValueList.getSelectedValues();
    if (myValueList.getSelectedIndex() == 0 || selection.length == 0) {
      return changeLists;
    }
    List<CommittedChangeList> result = new ArrayList<>();
    for (CommittedChangeList changeList : changeLists) {
      if (myProviderClass == null || myProviderClass.isInstance(changeList.getVcs().getCommittedChangesProvider())) {
        for (Object value : selection) {
          //noinspection unchecked
          if (value.toString().equals(myColumn.getValue(ReceivedChangeList.unwrap(changeList)).toString())) {
            result.add(changeList);
            break;
          }
        }
      }
    }
    return result;
  }

  private static class MyListModel extends AbstractListModel {
    private volatile String[] myValues;

    private MyListModel() {
      myValues = ArrayUtil.EMPTY_STRING_ARRAY;
    }

    public <T> void addNext(final Collection<T> values, final Convertor<T, String> convertor) {
      final TreeSet<String> set = new TreeSet<>(Arrays.asList(myValues));
      for (T value : values) {
        final String converted = convertor.convert(value);
        if (converted != null) {
          // also works as filter
          set.add(converted);
        }
      }
      myValues = ArrayUtil.toStringArray(set);
      fireContentsChanged(this, 0, myValues.length);
    }

    public int getSize() {
      return myValues.length + 1;
    }

    public boolean isEmpty() {
      return myValues.length == 0;
    }

    public Object getElementAt(int index) {
      if (index == 0) {
        return VcsBundle.message("committed.changes.filter.all");
      }
      return myValues[index - 1];
    }

    public void clear() {
      myValues = ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }
}
