class HelperVariable {

  void m() {
    StringBuilder sb<caret> = new StringBuilder();
    String t = "t";
    sb.append("this: ");
    sb.append(t);
    sb.append("that: ");
    sb.append(t);
    System.out.println(sb.toString());
  }
}