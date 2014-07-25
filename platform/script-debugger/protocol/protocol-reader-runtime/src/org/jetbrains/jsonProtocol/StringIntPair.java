package org.jetbrains.jsonProtocol;

public final class StringIntPair {
  public final String name;
  public final int value;

  public StringIntPair(String name, int value) {
    this.name = name;
    this.value = value;
  }

  public StringIntPair(int value, String name) {
    this(name, value);
  }
}
