enum G {
  VALUE(100),
  REF(VALUE/*1*/.<caret>getValue());

  private final int myValue;

  G(final int groupNumber) {
    myValue = groupNumber;
  }

  public int getValue() {
    return myValue;
  }
}