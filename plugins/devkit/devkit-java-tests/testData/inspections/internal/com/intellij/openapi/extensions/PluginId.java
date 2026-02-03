package com.intellij.openapi.extensions;

public final class PluginId {

  void test() {
    assert this == this;
    assert this != this;

    PluginId first = new PluginId();
    PluginId second = new PluginId();

    assert first == null;
    assert first != null;

    assert this == second;
    assert second != this;

    assert <warning descr="'PluginId' instances should be compared for equality, not identity">first == second</warning>;
    assert <warning descr="'PluginId' instances should be compared for equality, not identity">first != second</warning>;

    assert <warning descr="'PluginId' instances should be compared for equality, not identity">second == first</warning>;
    assert <warning descr="'PluginId' instances should be compared for equality, not identity">second != first</warning>;
  }
}