class SideEffect {

  public String checkForAssignment(String name) throws IOException {
    int len = name.length() * 2 + 1;
      <caret>StringBuilder sb3 = new StringBuilder(len);
      String sb = "1" +
              '/';
    name = sb;
    return name;
  }
}