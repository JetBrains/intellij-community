package com.intellij.vcs.log.compressedlist;

import com.intellij.vcs.log.compressedlist.generator.Generator;

import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class RunCompressedListTest<T> {
  private final CompressedList<T> compressedList;
  private final Generator<T> generator;

  public RunCompressedListTest(CompressedList<T> compressedList, Generator<T> generator) {
    this.compressedList = compressedList;
    this.generator = generator;
  }

  private String CompressedListStr() {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < compressedList.getList().size(); i++) {
      T listElement = compressedList.getList().get(i);
      s.append(listElement).append(" ");
    }
    return s.toString();
  }

  private String ActualListStr() {
    StringBuilder s = new StringBuilder();
    T t = generator.generateFirst();
    s.append(t).append(" ");
    for (int i = 1; i < compressedList.getList().size(); i++) {
      t = generator.generate(t, 1);
      s.append(t).append(" ");
    }
    return s.toString();
  }

  public void assertList() {
    assertList("");
  }

  public void assertList(String message) {
    assertEquals(message, ActualListStr(), CompressedListStr());
  }

  public void runReplace(UpdateRequest updateRequest) {
    compressedList.recalculate(updateRequest);
    assertList(replaceToStr(updateRequest));
  }


  public String replaceToStr(UpdateRequest updateRequest) {
    return "UpdateRequest: from: " +
           updateRequest.from() +
           ", to: " +
           updateRequest.to() +
           ", addedElementCounts: " +
           updateRequest.addedElementCount();
  }

}
