package com.siyeh.igtest.bugs.malformed_format_string;

import java.util.Locale;
import java.sql.Timestamp;

public class MalformedFormatString {

    public void foo()
    {
        String.format(<warning descr="Too many arguments for format string '\"%\"'">"%"</warning>, 3.0);
        System.out.printf(<warning descr="Too many arguments for format string '\"%\"'">"%"</warning>, 3.0);
        System.out.printf(<warning descr="Format string '\"%q\"' is malformed">"%q"</warning>, 3.0);
        System.out.printf(<warning descr="Format string '\"%d\"' does not match the type of its arguments">"%d"</warning>, 3.0);
        System.out.printf(new Locale(""),<warning descr="Format string '\"%d%s\"' does not match the type of its arguments">"%d%s"</warning>, 3.0, "foo");
    }

    public static void main(String[] args)  {
        String local = "hmm";

        String good = String.format("%s %s", 1, 2); // this is valid according to the inspector (correct)
        String warn = String.format(<warning descr="Too few arguments for format string '\"%s %s\"'">"%s %s"</warning>, 1); // this is invalid according to the inspector (correct)
        String invalid = String.format("%s %s" + local, 1); // this is valid according to the inspector (INCORRECT!)
        String interesting = String.format(<warning descr="Too few arguments for format string '\"%s %s\" + \"hmm\"'">"%s %s" + "hmm"</warning>, 1); // this is invalid according to the inspector (correct)
        String intAsChar = String.format("symbol '%1$c' (numeric value %1$d)", 60); // integer->char conversion is ok (correct)
    }

    public void outOfMemory() {
        String.format(<warning descr="Too few arguments for format string '\"%2147483640$s\"'">"%2147483640$s"</warning>, "s");
    }

    public void optionalSettings() {
        SomeOtherLogger logger = new SomeOtherLogger();
        logger.d(<warning descr="Too few arguments for format string '\"%s %s\"'">"%s %s"</warning>, 1); // this is invalid according to the inspector (correct)
    }

    public class SomeOtherLogger {
        public void d(String message, Object...args) {
            // Do some logging.
        }
    }

    void shouldWarn() {
        String.format(<warning descr="Format string '\"%1$c %1$d\"' does not match the type of its arguments">"%1$c %1$d"</warning>, 10L);
    }

    void shouldNotWarn() {
        String.format("%c", 0x10300);
        String charAsInt = String.format("%1$d %1$c", 10);  // int followed by char should be ok too.
    }

    String timestamp(Timestamp ts) {
        return String.format("%tF %tT", ts, ts);
    }
}
