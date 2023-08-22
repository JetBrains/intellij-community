// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.tree;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class FileNode {
  private final VirtualFile myFile;
  private final AtomicReference<Icon> myIcon = new AtomicReference<>();
  private final AtomicReference<String> myName = new AtomicReference<>();
  private final AtomicReference<String> myComment = new AtomicReference<>();
  private final AtomicBoolean myValid = new AtomicBoolean();
  private final AtomicBoolean myHidden = new AtomicBoolean();
  private final AtomicBoolean mySpecial = new AtomicBoolean();
  private final AtomicBoolean mySymlink = new AtomicBoolean();
  private final AtomicBoolean myWritable = new AtomicBoolean();

  FileNode(@NotNull VirtualFile file) {
    myFile = file;
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public Icon getIcon() {
    return myIcon.get();
  }

  boolean updateIcon(Icon icon) {
    return !Objects.equals(icon, myIcon.getAndSet(icon));
  }

  public @NlsSafe String getName() {
    return myName.get();
  }

  boolean updateName(String name) {
    return !Objects.equals(name, myName.getAndSet(name));
  }

  public @NlsSafe String getComment() {
    return myComment.get();
  }

  boolean updateComment(String comment) {
    return !Objects.equals(comment, myComment.getAndSet(comment));
  }

  public boolean isValid() {
    return myValid.get();
  }

  boolean updateValid(boolean valid) {
    return valid != myValid.getAndSet(valid);
  }

  public boolean isHidden() {
    return myHidden.get();
  }

  boolean updateHidden(boolean hidden) {
    return hidden != myHidden.getAndSet(hidden);
  }

  public boolean isSpecial() {
    return mySpecial.get();
  }

  boolean updateSpecial(boolean special) {
    return special != mySpecial.getAndSet(special);
  }

  public boolean isSymlink() {
    return mySymlink.get();
  }

  boolean updateSymlink(boolean symlink) {
    return symlink != mySymlink.getAndSet(symlink);
  }

  public boolean isWritable() {
    return myWritable.get();
  }

  boolean updateWritable(boolean writable) {
    return writable != myWritable.getAndSet(writable);
  }
}
