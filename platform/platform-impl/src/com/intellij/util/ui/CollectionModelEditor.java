/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PairProcessor;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OrderedSet;
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

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

  public void reset(@NotNull List<T> originalItems) {
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

  public void processModifiedItems(@NotNull final PairProcessor<T, T> processor) {
    // don't want to expose TObjectObjectProcedure - avoid implementation details
    helper.process(new TObjectObjectProcedure<T, T>() {
      @Override
      public boolean execute(T newItem, T oldItem) {
        return processor.process(newItem, oldItem);
      }
    });
  }

  @NotNull
  public final T getMutable(@NotNull T item) {
    return helper.getMutable(item, -1);
  }

  protected boolean isEditable(@NotNull T item) {
    return true;
  }

  protected class ModelHelper {
    final OrderedSet<T> originalItems = new OrderedSet<>(ContainerUtil.<T>identityStrategy());

    private final THashMap<T, T> modifiedToOriginal = new THashMap<>(ContainerUtil.<T>identityStrategy());
    private final THashMap<T, T> originalToModified = new THashMap<>(ContainerUtil.<T>identityStrategy());

    public void reset(@Nullable List<T> newOriginalItems) {
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

    public void process(@NotNull TObjectObjectProcedure<T, T> procedure) {
      modifiedToOriginal.forEachEntry(procedure);
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