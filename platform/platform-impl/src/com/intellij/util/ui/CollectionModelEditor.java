/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import gnu.trove.THashMap;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class CollectionModelEditor<T, E extends CollectionItemEditor<T>> implements ElementProducer<T> {
  protected static final Logger LOG = Logger.getInstance(CollectionModelEditor.class);

  protected final E itemEditor;
  protected final ModelHelper<T> helper = new ModelHelper<T>();

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

  @NotNull
  protected abstract List<T> getItems();

  public final boolean isModified(@NotNull List<T> oldItems) {
    List<T> items = getItems();
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

  public static class ModelHelper<T> {
    private final THashMap<T, T> modifiedToOriginal = new THashMap<T, T>();

    public void clear() {
      modifiedToOriginal.clear();
    }

    public void remove(@NotNull T item) {
      modifiedToOriginal.remove(item);
    }

    public boolean isMutable(@NotNull T item) {
      return modifiedToOriginal.containsKey(item);
    }

    @NotNull
    public T getMutable(@NotNull T item, @NotNull CollectionItemEditor<T> itemEditor) {
      if (isMutable(item)) {
        return item;
      }
      else {
        T mutable = itemEditor.clone(item, true);
        modifiedToOriginal.put(mutable, item);
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
}
