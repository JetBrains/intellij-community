class Test {
  String foo() {
    StringBuffer s<caret>b = new StringBuffer().append("a").append("|").append("b");
    return sb.toString();
  }
}