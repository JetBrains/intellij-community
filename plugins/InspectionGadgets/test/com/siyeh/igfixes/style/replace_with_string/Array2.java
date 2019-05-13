class Array {
  String array(char[] cs) {
    return new <caret>StringBuilder().append("cs: ").append(cs).toString();
  }
}
