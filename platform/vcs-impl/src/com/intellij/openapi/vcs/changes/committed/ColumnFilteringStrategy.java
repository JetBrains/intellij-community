// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.ChangeListColumn;
import com.intellij.openapi.vcs.CommittedChangesProvider;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.util.*;


@ApiStatus.Internal
public class ColumnFilteringStrategy implements ChangeListFilteringStrategy {
  private final JScrollPane myScrollPane;
  private final JList<@Nls String> myValueList;
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final ChangeListColumn myColumn;
  private final Class<? extends CommittedChangesProvider> myProviderClass;
  private final MyListModel myModel;
  private final CommittedChangeListToStringConvertor ourConvertorInstance = new CommittedChangeListToStringConvertor();

  private List<String> myPreferredSelection;

  public ColumnFilteringStrategy(final ChangeListColumn column,
                                 final Class<? extends CommittedChangesProvider> providerClass) {
    myModel = new MyListModel();
    myValueList = new JBList<>();
    myScrollPane = ScrollPaneFactory.createScrollPane(myValueList);
    myValueList.setModel(myModel);
    myValueList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(final ListSelectionEvent e) {
        for (ChangeListener listener : myListeners) {
          listener.stateChanged(new ChangeEvent(this));
        }
      }
    });
    myValueList.setCellRenderer(new ColoredListCellRenderer<>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, @Nls String value, int index, boolean selected, boolean hasFocus) {
        if (index == 0) {
          append(value, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else if (value.length() == 0) {
          append(VcsBundle.message("committed.changes.filter.none"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          append(value, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      }
    });
    myColumn = column;
    myProviderClass = providerClass;
  }

  @NotNull
  @Override
  public CommittedChangesFilterKey getKey() {
    return new CommittedChangesFilterKey(toString(), CommittedChangesFilterPriority.USER);
  }

  public String toString() {
    return myColumn.getTitle();
  }

  @Override
  @Nullable
  public JComponent getFilterUI() {
    return myScrollPane;
  }

  @Override
  public void setFilterBase(@NotNull List<? extends CommittedChangeList> changeLists) {
    myPreferredSelection = null;
    appendFilterBase(changeLists);
  }

  @Override
  public void addChangeListener(@NotNull ChangeListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(@NotNull ChangeListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void resetFilterBase() {
    myPreferredSelection = myValueList.getSelectedValuesList();
    myValueList.clearSelection();
    myModel.clear();
    myValueList.revalidate();
    myValueList.repaint();
  }

  @Override
  public void appendFilterBase(@NotNull List<? extends CommittedChangeList> changeLists) {
    final List<String> oldSelection = myModel.isEmpty() ? myPreferredSelection : myValueList.getSelectedValuesList();

    myModel.addNext(changeLists, ourConvertorInstance);
    if (oldSelection != null) {
      for (@Nls String o : oldSelection) {
        myValueList.setSelectedValue(o, false);
      }
    }
    myValueList.revalidate();
    myValueList.repaint();
  }

  private class CommittedChangeListToStringConvertor implements Convertor<CommittedChangeList, String> {
    @Override
    public String convert(CommittedChangeList o) {
      if (myProviderClass == null || myProviderClass.isInstance(o.getVcs().getCommittedChangesProvider())) {
        return myColumn.getValue(ReceivedChangeList.unwrap(o)).toString();
      }
      return null;
    }
  }

  @Override
  @NotNull
  public List<CommittedChangeList> filterChangeLists(@NotNull List<? extends CommittedChangeList> changeLists) {
    final List<String> selection = myValueList.getSelectedValuesList();
    if (myValueList.getSelectedIndex() == 0 || selection.isEmpty()) {
      return new ArrayList<>(changeLists);
    }
    List<CommittedChangeList> result = new ArrayList<>();
    for (CommittedChangeList changeList : changeLists) {
      if (myProviderClass == null || myProviderClass.isInstance(changeList.getVcs().getCommittedChangesProvider())) {
        for (String value : selection) {
          //noinspection unchecked
          if (value.equals(myColumn.getValue(ReceivedChangeList.unwrap(changeList)).toString())) {
            result.add(changeList);
            break;
          }
        }
      }
    }
    return result;
  }

  private static final class MyListModel extends AbstractListModel<@Nls String> {
    private volatile String[] myValues;

    private MyListModel() {
      myValues = ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    public <T> void addNext(final Collection<? extends T> values, final Convertor<? super T, @Nls String> convertor) {
      final TreeSet<String> set = new TreeSet<>(Arrays.asList(myValues));
      for (T value : values) {
        final String converted = convertor.convert(value);
        if (converted != null) {
          // also works as filter
          set.add(converted);
        }
      }
      myValues = ArrayUtilRt.toStringArray(set);
      fireContentsChanged(this, 0, myValues.length);
    }

    @Override
    public int getSize() {
      return myValues.length + 1;
    }

    public boolean isEmpty() {
      return myValues.length == 0;
    }

    @Override
    public String getElementAt(int index) {
      if (index == 0) {
        return VcsBundle.message("committed.changes.filter.all");
      }
      return myValues[index - 1];
    }

    public void clear() {
      myValues = ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
  }
}
