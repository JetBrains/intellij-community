class Test {

  public String toString() {
    StringBuilder builder<caret> = new StringBuilder();
    builder.append("a");
    builder  //comment
      .append("b");
    builder.append("c");
    return builder.toString();
  }
}