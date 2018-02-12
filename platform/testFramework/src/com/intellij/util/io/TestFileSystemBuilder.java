package com.intellij.util.io;

import org.jetbrains.annotations.NotNull;

/**
 * Consider using {@link com.intellij.util.io.DirectoryContentBuilder} instead, it provides more convenient Kotlin DSL.
 *
 * @author nik
 */
public class TestFileSystemBuilder {
  private final TestFileSystemItem myItem;
  private final TestFileSystemBuilder myParent;

  private TestFileSystemBuilder(TestFileSystemItem item, TestFileSystemBuilder parent) {
    myItem = item;
    myParent = parent;
  }

  @NotNull
  public TestFileSystemItem build() {
    TestFileSystemBuilder builder = this;
    while (builder.myParent != null) {
      builder = builder.myParent;
    }
    return builder.myItem;
  }

  @NotNull
  public TestFileSystemBuilder dir(String name) {
    final TestFileSystemItem item = new TestFileSystemItem(name, false, true);
    myItem.addChild(item);
    return new TestFileSystemBuilder(item, this);
  }

  @NotNull
  public TestFileSystemBuilder archive(String name) {
    final TestFileSystemItem item = new TestFileSystemItem(name, true, false);
    myItem.addChild(item);
    return new TestFileSystemBuilder(item, this);
  }

  @NotNull
  public TestFileSystemBuilder file(String name) {
    myItem.addChild(new TestFileSystemItem(name, false, false));
    return this;
  }

  @NotNull
  public TestFileSystemBuilder file(String name, String content) {
    myItem.addChild(new TestFileSystemItem(name, false, false, content));
    return this;
  }

  public TestFileSystemBuilder end() {
    return myParent;
  }

  @NotNull
  public static TestFileSystemBuilder fs() {
    return new TestFileSystemBuilder(new TestFileSystemItem("root", false, true), null);
  }
}
