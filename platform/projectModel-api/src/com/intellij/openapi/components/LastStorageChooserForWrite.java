package com.intellij.openapi.components;

import com.intellij.util.ArrayUtil;

public final class LastStorageChooserForWrite implements StateStorageChooser<Object> {
  public static final LastStorageChooserForWrite INSTANCE = new LastStorageChooserForWrite();

  @Override
  public Storage[] selectStorages(Storage[] storages, Object component, StateStorageOperation operation) {
    return operation == StateStorageOperation.WRITE ? new Storage[]{storages[storages.length - 1]} : storages;
  }

  // our DefaultStateSerializer.deserializeState doesn't perform merge states if state is Element
  public final static class ElementStateLastStorageChooserForWrite implements StateStorageChooser<Object> {
    @Override
    public Storage[] selectStorages(Storage[] storages, Object component, StateStorageOperation operation) {
      if (operation == StateStorageOperation.WRITE) {
        return new Storage[]{storages[storages.length - 1]};
      }
      else {
        return ArrayUtil.reverseArray(storages);
      }
    }
  }
}