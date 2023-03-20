class SideEffect {

  public String checkForAssignment(String name) throws IOException {
    int len = name.length() * 2 + 1;
    StringBuilder s<caret>b = new StringBuilder(len);
    StringBuilder sb3 = new StringBuilder(len);
    sb.append(1);
    name = sb
      .append('/')
      .toString();
    return name;
  }
}