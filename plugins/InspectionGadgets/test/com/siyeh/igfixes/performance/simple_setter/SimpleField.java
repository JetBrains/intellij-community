class XXX {
  int value = 10;
  int i = <caret>getValue(/*1*/);

  public int getValue() {
    return value;
  }
}