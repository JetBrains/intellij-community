import java.util.regex.Pattern;

class MalformedRegex {
  Pattern BACKSLASH_PATTERN = Pattern.compile("<error descr="Illegal/unsupported escape sequence">\</error>\" , Pattern.LITERAL);

  private static final String ASTERISK = "<error descr="Dangling metacharacter">*</error>";

  public void foo() {
    "bar".matches(".*");
    Pattern.compile(<warning descr="Regular expression 'ASTERISK' is malformed: Dangling meta character '*'">ASTERISK</warning>);
  }
}