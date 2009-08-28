package com.intellij.openapi.components.impl.stores;

public class StoresFactory {
  private StoresFactory() {
  }

  public static Class getProjectStoreClass(final boolean aDefault) {
    return aDefault ? DefaultProjectStoreImpl.class : ProjectStoreImpl.class;
  }

  public static Class getApplicationStoreClass() {
    return ApplicationStoreImpl.class;
  }
}
