package com.siyeh.igtest.bugs.malformed_format_string;

import java.util.Locale;
import java.sql.Timestamp;

public class MalformedFormatString {

    public void foo()
    {
        String.<warning descr="Too many arguments for format string (found: 1, expected: 0)">format</warning>("%", 3.0);
        System.out.<warning descr="Too many arguments for format string (found: 1, expected: 0)">printf</warning>("%", 3.0);
        System.out.printf(<warning descr="Illegal format string specifier '%q'">"%q"</warning>, 3.0);
        System.out.printf("%d", <warning descr="Argument type 'double' does not match the type of the format specifier '%d'">3.0</warning>);
        System.out.printf(new Locale(""),"%d%s", <warning descr="Argument type 'double' does not match the type of the format specifier '%d'">3.0</warning>, "foo");
    }

    public static void main(String[] args)  {
        String local = "hmm";

        String good = String.format("%s %s", 1, 2); // this is valid according to the inspector (correct)
        String warn = String.<warning descr="Too few arguments for format string (found: 1, expected: 2)">format</warning>("%s %s", 1); // this is invalid according to the inspector (correct)
        String invalid = String.format("%s %s" + local, 1); // this is valid according to the inspector (INCORRECT!)
        String interesting = String.<warning descr="Too few arguments for format string (found: 1, expected: 2)">format</warning>("%s %s" + "hmm", 1); // this is invalid according to the inspector (correct)
        String intAsChar = String.format("symbol '%1$c' (numeric value %1$d)", 60); // integer->char conversion is ok (correct)
    }

    public void outOfMemory() {
        String.<warning descr="Too few arguments for format string (found: 1, expected: 2)">format</warning>("%2147483640$s", "s");
    }

    public void optionalSettings() {
        SomeOtherLogger logger = new SomeOtherLogger();
        logger.<warning descr="Too few arguments for format string (found: 1, expected: 2)">d</warning>("%s %s", 1); // this is invalid according to the inspector (correct)
    }

    public class SomeOtherLogger {
        public void d(String message, Object...args) {
            // Do some logging.
        }
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

        // flags on newline not allowed
        String.format(<warning descr="Illegal format string specifier '%(n'">"%(n"</warning>);

        // unknown conversion
        String.format(<warning descr="Illegal format string specifier '%D'">"%D"</warning>, 1);

        // duplicate leading space flag
        String.format(<warning descr="Illegal format string specifier '%  d'">"%  d"</warning>, 1);

        // illegal alternate flag
        String.format(<warning descr="Illegal format string specifier '%#B'">"%#B"</warning>, true);

        // illegal flag combination
        String.format(<warning descr="Illegal format string specifier '% +d'">"% +d"</warning>, 1);

        // illegal flag on date/time
        String.format(<warning descr="Illegal format string specifier '%+T'">"%+T"</warning>, new Timestamp(0));

        // previous flag without previous
        String.format(<warning descr="Illegal format string specifier '%<s'">"%<s"</warning>, 1);

        // illegal flag
        String.format(<warning descr="Illegal format string specifier '%(s'">"%(s"</warning>, 1);

        // unknown format conversions
        String.format(<warning descr="Illegal format string specifier '%F'">"%F"</warning>, 1.0);
        String.format(<warning descr="Illegal format string specifier '%D'">"%D"</warning>, 1);
        String.format(<warning descr="Illegal format string specifier '%O'">"%O"</warning>, 1);
    }

    void goodStrings() {
        String.format("%-B", true); // left justify flag
        String.format("%,d", 34567890);
        System.out.printf("%tF %n", java.time.ZonedDateTime.now()); // java.time.temporal.TemporalAccessor, new in Java 8
    }
}
