package com.siyeh.igtest.bugs.malformed_format_string;

import java.util.Locale;


public class MalformedFormatString {

    public void foo()
    {
        String.format("%", 3.0);
        System.out.printf("%", 3.0);
        System.out.printf("%q", 3.0);
        System.out.printf("%d", 3.0);
        System.out.printf(new Locale(""),"%d%s", 3.0, "foo");
    }

    public static void main(String[] args)  {
        String local = "hmm";

        String good = String.format("%s %s", 1, 2); // this is valid according to the inspector (correct)
        String warn = String.format("%s %s", 1); // this is invalid according to the inspector (correct)
        String invalid = String.format("%s %s" + local, 1); // this is valid according to the inspector (INCORRECT!)
        String interesting = String.format("%s %s" + "hmm", 1); // this is invalid according to the inspector (correct)
        String intAsChar = String.format("symbol '%1$c' (numeric value %1$d)", 60); // integer->char conversion is ok (correct)
    }

    public void outOfMemory() {
        String.format("%2147483640$s", "s");
    }

    public void optionalSettings() {
        SomeOtherLogger logger = new SomeOtherLogger();
        logger.d("%s %s", 1); // this is invalid according to the inspector (correct)
    }

    public class SomeOtherLogger {
        public void d(String message, Object...args) {
            // Do some logging.
        }
    }
}
