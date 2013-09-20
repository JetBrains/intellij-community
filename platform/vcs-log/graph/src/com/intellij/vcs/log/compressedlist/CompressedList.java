package com.intellij.vcs.log.compressedlist;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author erokhins
 */
public interface CompressedList<T> {

  // unmodifiableList
  @NotNull
  public List<T> getList();

  public void recalculate(@NotNull UpdateRequest updateRequest);
}
