class Demo {
  private Object field;

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    else {
      Demo other = (Demo)obj;
      return field <caret>== other.field;
    }
  }
}