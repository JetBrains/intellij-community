package com.intellij.vcs.log.compressedlist;

import com.intellij.vcs.log.compressedlist.generator.Generator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author erokhins
 */
public class NoCompressedList<T> implements CompressedList<T> {
  private final List<T> calcList;
  private final Generator<T> generator;
  private int size;

  public NoCompressedList(Generator<T> generator, int size) {
    assert size >= 0 : "bad size";
    calcList = new ArrayList<T>(size);
    this.generator = generator;
    this.size = size;
    generate();
  }

  private void generate() {
    calcList.clear();
    T t = generator.generateFirst();
    calcList.add(t);
    for (int i = 1; i < size; i++) {
      t = generator.generate(t, 1);
      calcList.add(t);
    }
  }

  @NotNull
  @Override
  public List<T> getList() {
    return Collections.unmodifiableList(calcList);
  }


  @Override
  public void recalculate(@NotNull UpdateRequest updateRequest) {
    if (updateRequest == UpdateRequest.ID_UpdateRequest) {
      return;
    }
    if (updateRequest.to() >= size) {
      throw new IllegalArgumentException("Bad updateRequest: " + updateRequest.from() + ", " +
                                         +updateRequest.to() + ", " + updateRequest.addedElementCount());
    }
    size = updateRequest.addedElementCount() - updateRequest.removedElementCount() + size;
    generate();
  }
}
