import java.io.File;

class SuspicousRegexExpressionArgument {{

  "a.s.d.f".split("\\.");
  "vb|amna".replaceAll("\\|", "-");
  "1+2+3".split("\\+");
  "one two".split(" ");
  "[][][]".split("]");
  "{}{}{}".split("}");
  
  "a/b/c/d".split(File.separator);
}}