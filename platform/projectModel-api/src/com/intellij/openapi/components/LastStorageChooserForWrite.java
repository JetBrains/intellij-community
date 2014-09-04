package com.intellij.openapi.components;

public final class LastStorageChooserForWrite implements StateStorageChooser<Object> {
  public static final LastStorageChooserForWrite INSTANCE = new LastStorageChooserForWrite();

  @Override
  public Storage[] selectStorages(Storage[] storages, Object component, StateStorageOperation operation) {
    return operation == StateStorageOperation.WRITE ? new Storage[]{storages[storages.length - 1]} : storages;
  }
}