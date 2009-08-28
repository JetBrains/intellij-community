package com.intellij.openapi.components.impl.stores;


public interface ComponentVersionProvider {
  ComponentVersionProvider EMPTY = new ComponentVersionProvider(){
    public long getVersion(String name) {
      return 0;
    }

    public void changeVersion(String name, long version) {
    }
  };

  long getVersion(String name);
  void changeVersion(String name, long version);
}
