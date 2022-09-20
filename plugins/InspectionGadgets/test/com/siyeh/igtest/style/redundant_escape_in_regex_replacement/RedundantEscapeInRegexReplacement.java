import java.util.regex.*;

class RedundantEscapeInRegexReplacement {

  void x() {
    "test".replaceAll("$", "\\$");
    "test".replaceAll("\\\\", "\\\\");
    "s".replaceAll("\\\"", "\\\\<warning descr="Redundant escape of '\"'">\\</warning>\"");
    "a".replaceFirst("a", "<warning descr="Redundant escape of 'b'">\\</warning>b");
    Matcher m = Pattern.compile("a").matcher("hi!");
    m.replaceAll("<warning descr="Redundant escape of '!'">\\</warning>!");
    m.replaceFirst("<warning descr="Redundant escape of '@'">\\</warning>@");
    m.appendReplacement(new StringBuilder(), "<warning descr="Redundant escape of '#'">\\</warning>#");
    m.appendReplacement(new StringBuffer(), "<warning descr="Redundant escape of '%'">\\</warning>%");
    "x".replaceAll("x", "<warning descr="Redundant escape of '.'">\\</warning>.<warning descr="Redundant escape of '.'">\\</warning>.");
  }
}