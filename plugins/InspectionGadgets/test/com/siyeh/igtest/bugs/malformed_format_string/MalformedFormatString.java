package com.siyeh.igtest.bugs.malformed_format_string;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Locale;
import java.util.Formattable;

public class MalformedFormatString {

    public void foo() {
        String.<warning descr="Too many arguments for format string (found: 1, expected: 0)">format</warning>("%%", 3.0);
        System.out.<warning descr="Too many arguments for format string (found: 2, expected: 1)">printf</warning>("%s", 3.0, 2.0);
        System.out.printf(<warning descr="Illegal format string specifier: unknown conversion in '%q'">"%q"</warning>, 3.0);
        System.out.printf("%d", <warning descr="Argument type 'double' does not match the type of the format specifier '%d'">3.0</warning>);
        System.out.printf(new Locale(""),"%d%s", <warning descr="Argument type 'double' does not match the type of the format specifier '%d'">3.0</warning>, "foo");
    }

    public static void main(String[] args) {
        String local = "hmm";

        String good = String.format("%s %s", 1, 2); // this is valid according to the inspector (correct)
        String warn = String.<warning descr="Too few arguments for format string (found: 1, expected: 2)">format</warning>("%s %s", 1); // this is invalid according to the inspector (correct)
        String invalid = String.<warning descr="Too few arguments for format string (found: 1, expected at least: 2)">format</warning>("%s %s " + local, 1); // this is valid according to the inspector (INCORRECT!)
        String interesting = String.<warning descr="Too few arguments for format string (found: 1, expected: 2)">format</warning>("%s %s" + "hmm", 1); // this is invalid according to the inspector (correct)
        String intAsChar = String.format("symbol '%1$c' (numeric value %1$d)", 60); // integer->char conversion is ok (correct)
    }

    public void outOfMemory() {
        String.<warning descr="Too few arguments for format string (found: 1, expected: 2)">format</warning>("%2147483640$s", "s");
    }

    public void optionalSettings() {
        SomeOtherLogger logger = new SomeOtherLogger();
        logger.<warning descr="Too few arguments for format string (found: 1, expected: 2)">d</warning>("%s %s", 1); // this is invalid according to the inspector (correct)
        logger.d(new Exception(), <warning descr="Illegal format string specifier: flag '0' not allowed in '%0s'">"%0s"</warning>, "7");
    }

    public class SomeOtherLogger {
        public void d(String message, Object...args) {
            // Do some logging.
        }

        public void d(Throwable t, String message, Object args) {}
    }

    void shouldWarn() {
        String.format("%1$c %1$d", <warning descr="Argument type 'long' does not match the type of the format specifier '%1$c'">10L</warning>);
    }

    void shouldNotWarn() {
        String.format("%c", 0x10300);
        String charAsInt = String.format("%1$d %1$c", 10);  // int followed by char should be ok too.
    }

    String timestamp(Timestamp ts) {
        return String.format("%tF %tT", ts, ts);
    }

    void badStrings() {
        // bad format specifier
        String.format(<warning descr="Format string '\"%) %n\"' is malformed">"%) %n"</warning>);
        String.format(<warning descr="Format string '\"%d%\"' is malformed">"%d%"</warning>, 1);

        // flags on newline not allowed
        String.format(<warning descr="Illegal format string specifier: flag '(' not allowed in '%(n'">"%(n"</warning>);

        // unknown conversion
        String.format(<warning descr="Illegal format string specifier: unknown conversion in '%D'">"%D"</warning>, 1);

        // duplicate leading space flag
        String.format(<warning descr="Illegal format string specifier: duplicate flag ' ' in '%  d'">"%  d"</warning>, 1);

        // illegal alternate flag
        String.format(<warning descr="Illegal format string specifier: flag '#' not allowed in '%#B'">"%#B"</warning>, true);

        // illegal flag combination
        String.format(<warning descr="Illegal format string specifier: illegal flag combination ' ' and '+' in '% +d'">"% +d"</warning>, 1);
        String.format(<warning descr="Illegal format string specifier: illegal flag combination '-' and '0' in '%0-5e'">"%0-5e"</warning>, 1000.1);

        // illegal flag on date/time
        String.format(<warning descr="Illegal format string specifier: unknown conversion in '%+T'">"%+T"</warning>, new Timestamp(0));

        // previous flag without previous
        String.format(<warning descr="Illegal format string specifier: previous flag '<' used but no previous format specifier found for '%<s'">"%<s"</warning>, 1);

        // illegal flag
        String.format(<warning descr="Illegal format string specifier: flag '(' not allowed in '%(s'">"%(s"</warning>, 1);

        // unknown format conversions
        String.format(<warning descr="Illegal format string specifier: unknown conversion in '%F'">"%F"</warning>, 1.0);
        String.format(<warning descr="Illegal format string specifier: unknown conversion in '%D'">"%D"</warning>, 1);
        String.format(<warning descr="Illegal format string specifier: unknown conversion in '%O'">"%O"</warning>, 1);

        // left justify without width
        String.format(<warning descr="Illegal format string specifier: left justify flag '-' used but width not specified in '%-B'">"%-B"</warning>, true); // left justify flag
        String.format(<warning descr="Illegal format string specifier: zero padding flag '0' used but width not specified in '%0e'">"%0e"</warning>, 1.1);

        // precision not allowed
        String.format(<warning descr="Illegal format string specifier: precision ('.2') not allowed in '%.2n'">"%.2n"</warning>);
        String.format(<warning descr="Illegal format string specifier: precision ('.3') not allowed in '%.3%'">"%.3%"</warning>);
        String.format(<warning descr="Illegal format string specifier: precision ('.4') not allowed in '%.4tT'">"%.4tT"</warning>, new Date());
        String.format(<warning descr="Illegal format string specifier: precision ('.5') not allowed in '%.5c'">"%.5c"</warning>, '\u00A9');
        String.format(<warning descr="Illegal format string specifier: precision ('.6') not allowed in '%.6x'">"%.6x"</warning>, 15);
        String.format(<warning descr="Illegal format string specifier: precision ('.1') not allowed in '%.1d'">"%.1d"</warning>, 1);

        //date-time conversions not allowed
        String.format(<warning descr="Illegal format string specifier: unknown conversion in 'tX'">"%tX"</warning>, java.time.LocalDateTime.now());
        String.format("%tz", <warning descr="Argument type 'LocalDateTime' does not match the type of the format specifier '%tz'">java.time.LocalDateTime.now()</warning>);
        String.format("%ts", <warning descr="Argument type 'LocalDateTime' does not match the type of the format specifier '%ts'">java.time.LocalDateTime.now()</warning>);
        String.format("%tH", <warning descr="Argument type 'LocalDate' does not match the type of the format specifier '%tH'">java.time.LocalDate.now()</warning>);
        String.format("%th", <warning descr="Argument type 'LocalTime' does not match the type of the format specifier '%th'">java.time.LocalTime.now()</warning>);
        String.format("%ta", <warning descr="Argument type 'OffsetTime' does not match the type of the format specifier '%ta'">java.time.OffsetTime.now()</warning>);
    }

    void goodStrings() {
        String.format("%,d", 34567890);
        System.out.printf("%tF %n", java.time.ZonedDateTime.now()); // java.time.temporal.TemporalAccessor, new in Java 8
        System.out.printf("%tI", java.time.LocalDateTime.now());
        System.out.printf("%tB", java.time.LocalDate.now());
        System.out.printf("%tR", java.time.LocalTime.now());
        System.out.printf("%tZ", java.time.OffsetDateTime.now());
    }

    void previousFlag() {
        System.out.printf("%s %<s", "A");
        System.out.printf(<warning descr="Illegal format string specifier: previous flag '<' used but no previous format specifier found for '%<s'">"%<s"</warning>, "A");
        System.out.printf("%tT %<tT", new Date());
        System.out.printf("%b %<B", true);
        System.out.printf("%h %<H", 15);
        System.out.printf("%c %<C", '\u00A9');
        System.out.printf("%o %<o", 15);
    }

    private int count1;
    private int count2;

    public String highlightBothArguments() {
        return String.format("count 1: %f, count 2: %f", <warning descr="Argument type 'int' does not match the type of the format specifier '%f'">count1</warning>, <warning descr="Argument type 'int' does not match the type of the format specifier '%f'">count2</warning>);
    }

    void arrayArguments() {
        String.format("%c", new Object[]{'a'});
        String.<warning descr="Too many arguments for format string (found: 2, expected: 1)">format</warning>("%c", new Object[]{'a', 'b'});
        String.<warning descr="Too few arguments for format string (found: 1, expected: 2)">format</warning>("%c %c", new Object[]{'a'});
        Object[] array = new Object[]{<warning descr="Argument type 'String' does not match the type of the format specifier '%#s'">"the void"</warning>};
        String.format("%#s", array);
        Object[] array2 = {<warning descr="Argument type 'String' does not match the type of the format specifier '%#s'">"the void"</warning>};
        String.format("%#s", array2);
    }

    void constrainedType(Object obj) {
        if(obj instanceof Integer) {
            String.format("%6d", obj);
        } else if(obj instanceof Double) {
            String.format("%6f", obj);
        } else {
            String.format("%d", <warning descr="Argument type 'Object' does not match the type of the format specifier '%d'">obj</warning>);
        }
    }
}
class A {
    void m(Formattable f) {
        // each one fails, but inspection doesn't find the problems
        String.format("%#s", <warning descr="Argument type 'int' does not match the type of the format specifier '%#s'">0</warning>);
        String.format("%#s", <warning descr="Argument type 'float' does not match the type of the format specifier '%#s'">0.5f</warning>);
        String.format("%#S", <warning descr="Argument type 'String' does not match the type of the format specifier '%#S'">"hello"</warning>);
        String.format("%#S", <warning descr="Argument type 'int' does not match the type of the format specifier '%#S'">new Object().hashCode()</warning>);

        // should be OK
        String.format("%#s", f);
    }
}


class Prefix{
  void foo(String additionalText){
    System.out.printf("test" + getSomeText(additionalText), "parameter1");
    System.out.printf("test" + additionalText, "parameter1");
    String newString = "test" + additionalText;
    System.out.printf(newString, "parameter1");
    System.out.printf("test test" + additionalText, "parameter1");

    System.out.printf("test %s " + getSomeText(additionalText), "parameter1");
    System.out.printf("test %s " + additionalText, "parameter1");
    String newString2 = "test %s " + additionalText;
    System.out.printf(newString2, "parameter1");
    System.out.printf("test test %s " + additionalText, "parameter1");

    System.out.printf("test test %s %" + additionalText, "parameter1");
    System.out.printf("test test %s %-1500" + additionalText, "parameter1");
  }

  void bar(String additionalText){
    System.out.<warning descr="Too few arguments for format string (found: 1, expected at least: 2)">printf</warning>("test %s %s " + getSomeText(additionalText), "parameter1");
    System.out.<warning descr="Too few arguments for format string (found: 1, expected at least: 2)">printf</warning>("test %s %s" + getSomeText(additionalText), "parameter1");
    System.out.<warning descr="Too few arguments for format string (found: 1, expected at least: 2)">printf</warning>("test %s %s " + additionalText, "parameter1");
    String newString2 = "test %s %s " + additionalText;
    System.out.printf(newString2, "parameter1");
    System.out.<warning descr="Too few arguments for format string (found: 1, expected at least: 2)">printf</warning>("test test %s %s " + additionalText, "parameter1");
    System.out.printf("test test %d " + additionalText, <warning descr="Argument type 'String' does not match the type of the format specifier '%d'">"parameter1"</warning>);
  }

  String getSomeText(String text){
    return text;
  }
}