package com.intellij.openapi.vcs.changes.committed;

import com.intellij.util.PairProcessor;

/**
* @author irengrig
*/
public class MockRecordingPairProcessor implements PairProcessor<String, Integer> {
  private Integer myValue;
  private String myKey;

  @Override
  public boolean process(final String s, final Integer integer) {
    myKey = s;
    myValue = integer;
    return true;
  }

  public String getKey() {
    return myKey;
  }

  public Integer getValue() {
    return myValue;
  }
}
