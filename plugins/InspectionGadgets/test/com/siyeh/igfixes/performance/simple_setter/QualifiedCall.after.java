enum G {
  VALUE(100),
    REF(VALUE.myValue);

    /*1*/
    private final int myValue;

  G(final int groupNumber) {
    myValue = groupNumber;
  }

  public int getValue() {
    return myValue;
  }
}