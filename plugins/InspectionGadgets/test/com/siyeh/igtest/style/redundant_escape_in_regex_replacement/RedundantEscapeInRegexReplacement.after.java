import java.util.regex.*;

class RedundantEscapeInRegexReplacement {

  void x() {
    "test".replaceAll("$", "\\$");
    "test".replaceAll("\\\\", "\\\\");
    "s".replaceAll("\\\"", "\\\\\"");
    "a".replaceFirst("a", "b");
    Matcher m = Pattern.compile("a").matcher("hi!");
    m.replaceAll("!");
    m.replaceFirst("@");
    m.appendReplacement(new StringBuilder(), "#");
    m.appendReplacement(new StringBuffer(), "%");
    "x".replaceAll("x", ".\\.");

    final String replacement = "\\[";
    "x".replaceAll("x", replacement);
  }
}