// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

public abstract class CollectionModelEditor<T, E extends CollectionItemEditor<T>> implements ElementProducer<T> {
  protected static final Logger LOG = Logger.getInstance(CollectionModelEditor.class);

  protected final E itemEditor;
  protected final ModelHelper helper = new ModelHelper();

  protected CollectionModelEditor(@NotNull E itemEditor) {
    this.itemEditor = itemEditor;
  }

  @Override
  public T createElement() {
    return ReflectionUtil.newInstance(itemEditor.getItemClass());
  }

  @Override
  public boolean canCreateElement() {
    return true;
  }

  /**
   * Mutable internal list of items (must not be exposed to client)
   */
  @NotNull
  protected abstract List<T> getItems();

  public void reset(@NotNull List<? extends T> originalItems) {
    helper.reset(originalItems);
  }

  public final boolean isModified() {
    List<T> items = getItems();
    OrderedSet<T> oldItems = helper.originalItems;
    if (items.size() != oldItems.size()) {
      return true;
    }
    else {
      for (int i = 0, size = items.size(); i < size; i++) {
        if (!items.get(i).equals(oldItems.get(i))) {
          return true;
        }
      }
    }
    return false;
  }

  public void processModifiedItems(@NotNull BiPredicate<T, T> processor) {
    // don't want to expose TObjectObjectProcedure - avoid implementation details
    helper.process(processor);
  }

  @NotNull
  public final T getMutable(@NotNull T item) {
    return helper.getMutable(item, -1);
  }

  protected boolean isEditable(@NotNull T item) {
    return true;
  }

  protected final class ModelHelper {
    final OrderedSet<T> originalItems = new OrderedSet<>(ContainerUtil.identityStrategy());

    private final Map<T, T> modifiedToOriginal = new IdentityHashMap<>();
    private final Map<T, T> originalToModified = new IdentityHashMap<>();

    public void reset(@Nullable List<? extends T> newOriginalItems) {
      if (newOriginalItems != null) {
        originalItems.clear();
        originalItems.addAll(newOriginalItems);
      }
      modifiedToOriginal.clear();
      originalToModified.clear();
    }

    public void remove(@NotNull T item) {
      T original = modifiedToOriginal.remove(item);
      if (original != null) {
        originalToModified.remove(original);
      }
    }

    public boolean isMutable(@NotNull T item) {
      return modifiedToOriginal.containsKey(item) || !originalItems.contains(item);
    }

    @NotNull
    public T getMutable(@NotNull T item, int index) {
      if (isMutable(item) || !isEditable(item)) {
        return item;
      }
      else {
        T mutable = originalToModified.get(item);
        if (mutable == null) {
          mutable = itemEditor.clone(item, false);
          modifiedToOriginal.put(mutable, item);
          originalToModified.put(item, mutable);
          silentlyReplaceItem(item, mutable, index);
        }
        return mutable;
      }
    }

    public boolean hasModifiedItems() {
      return !modifiedToOriginal.isEmpty();
    }

    public void process(@NotNull BiPredicate<T, T> procedure) {
      for (Map.Entry<T, T> entry : modifiedToOriginal.entrySet()) {
        T key = entry.getKey();
        T value = entry.getValue();
        if (!procedure.test(key, value)) {
          break;
        }
      }
    }
  }

  protected void silentlyReplaceItem(@NotNull T oldItem, @NotNull T newItem, int index) {
    // silently replace item
    List<T> items = getItems();
    items.set(index == -1 ? ContainerUtil.indexOfIdentity(items, oldItem) : index, newItem);
  }

  protected final boolean areSelectedItemsRemovable(@NotNull ListSelectionModel selectionMode) {
    int minSelectionIndex = selectionMode.getMinSelectionIndex();
    int maxSelectionIndex = selectionMode.getMaxSelectionIndex();
    if (minSelectionIndex < 0 || maxSelectionIndex < 0) {
      return false;
    }

    List<T> items = getItems();
    for (int i = minSelectionIndex; i <= maxSelectionIndex; i++) {
      if (itemEditor.isRemovable(items.get(i))) {
        return true;
      }
    }
    return false;
  }
}