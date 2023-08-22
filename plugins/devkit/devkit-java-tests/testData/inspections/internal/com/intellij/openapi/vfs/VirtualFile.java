package com.intellij.openapi.vfs;

public abstract class VirtualFile {

  void test() {
    assert this == this;
    assert this != this;

    VirtualFileImpl first = new VirtualFileImpl();
    VirtualFile second = new VirtualFileImpl();

    assert first == null;
    assert first != null;

    assert this == second;
    assert second != this;

    assert <warning descr="'VirtualFile' instances should be compared for equality, not identity">first == second</warning>;
    assert <warning descr="'VirtualFile' instances should be compared for equality, not identity">first != second</warning>;

    assert <warning descr="'VirtualFile' instances should be compared for equality, not identity">second == first</warning>;
    assert <warning descr="'VirtualFile' instances should be compared for equality, not identity">second != first</warning>;
  }

  static final class VirtualFileImpl extends VirtualFile {}
}