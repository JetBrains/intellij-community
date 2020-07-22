import java.util.Collection;

class IgnoredContent {
    String s1 = "<html>";
    String s2 = "<b>";
    String s3 = "</b>";
    String s4 = <warning descr="Hardcoded string literal: \"<b>Hello</b>\"">"<b>Hello</b>"</warning>;
    String s5 = "https://www.example.com/";
    String s6 = <warning descr="Hardcoded string literal: \"See URL: https://www.example.com/\"">"See URL: https://www.example.com/"</warning>;
    String s7 = "foo.bar.baz";
    String s8 = "access$";
    String s9 = "<html>" + s8 + "</html>";
    String s10 = "&lt;";
}