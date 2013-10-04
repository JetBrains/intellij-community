class CharLiteral {
  void literal() {
    StringBuilder <caret>sb = new StringBuilder().append('\t');
    System.out.println(sb.toString());
  }
}