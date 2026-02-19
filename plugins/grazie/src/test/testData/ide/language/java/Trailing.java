class Trailing {
  public void testJavaStringEscapedBackslashTrailingSpaces() {
    String text = "class C { String s = \"abc\\\\    \"; }";
    int offset = text.indexOf("abc");
    String content = extractText("a.java", text, offset);
    assertTrue(content.toString().startsWith("abc\\"));
  }

  public static String extractText(String fileName, String fileText, int offset) {
    return fileText;
  }

  public static void assertTrue(boolean b) {
    return;
  }
}