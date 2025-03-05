// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorListRendererContextProvider;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.*;
import java.util.function.Function;

@ApiStatus.Internal
public final class FileTypeWithDescriptorRenderer<T> extends SimpleListCellRenderer<T> implements UiInspectorListRendererContextProvider {
  private static final Icon EMPTY_ICON = EmptyIcon.ICON_18;

  private final @NotNull ListModel<? extends T> myModel;
  private final Function<? super T, ? extends FileType> myConverter;
  private @Nullable Set<String> myDuplicateDescriptions;

  public FileTypeWithDescriptorRenderer(@NotNull ListModel<? extends T> model, @NotNull Function<? super T, ? extends FileType> converter) {
    myModel = model;
    myConverter = converter;
    model.addListDataListener(new ListDataListener() {
      @Override public void intervalAdded(ListDataEvent e) { myDuplicateDescriptions = null; }
      @Override public void intervalRemoved(ListDataEvent e) { myDuplicateDescriptions = null; }
      @Override public void contentsChanged(ListDataEvent e) { myDuplicateDescriptions = null; }
    });
  }

  @Override
  public void customize(@NotNull JList<? extends T> list, T t, int index, boolean selected, boolean hasFocus) {
    LayeredIcon layeredIcon = new LayeredIcon(2);
    layeredIcon.setIcon(EMPTY_ICON, 0);
    FileType value = myConverter.apply(t);
    Icon icon = value.getIcon();
    if (icon != null) {
      layeredIcon.setIcon(icon, 1, (-icon.getIconWidth() + EMPTY_ICON.getIconWidth()) / 2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight()) / 2);
    }
    setIcon(layeredIcon);

    String description = value.getDescription();
    if (isDuplicated(description)) {
      setText(description + " (" + value.getName() + ")");  // NON-NLS (in this case, the name is acceptable)
    }
    else {
      setText(description);
    }
  }

  private boolean isDuplicated(String description) {
    if (myDuplicateDescriptions == null) {
      myDuplicateDescriptions = new HashSet<>();
      Set<String> filter = new HashSet<>();
      for (int i = 0; i < myModel.getSize(); i++) {
        String s = myConverter.apply(myModel.getElementAt(i)).getDescription();
        if (!filter.add(s)) myDuplicateDescriptions.add(s);
      }
    }

    return myDuplicateDescriptions.contains(description);
  }

  @Override
  public @NotNull List<PropertyBean> getUiInspectorContext(@NotNull JList<?> list, @Nullable Object value, int index) {
    //noinspection unchecked
    FileType fileType = myConverter.apply((T)value);
    if (fileType == null) return Collections.emptyList();

    List<PropertyBean> result = new ArrayList<>();
    result.add(new PropertyBean("FileType ID", fileType.getName(), true));
    result.add(new PropertyBean("FileType Class", UiInspectorUtil.getClassPresentation(fileType), true));
    return result;
  }

  public void resetDuplicates() {
    myDuplicateDescriptions = null;
  }
}
