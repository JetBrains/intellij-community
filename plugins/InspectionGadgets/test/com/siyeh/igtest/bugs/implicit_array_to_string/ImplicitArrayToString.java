package com.siyeh.igtest.bugs.implicit_array_to_string;

import java.io.PrintWriter;
import java.util.Formatter;




public class ImplicitArrayToString {

    void foo() {
        String[] interfaces = {"java/util/Set", "java/util/List"};
        System.out.println("interfaces: " + interfaces); // triggered
        System.out.printf("interfaces: %s\n", interfaces); // not triggered
        System.out.printf("interfaces: %s, count: %s", interfaces, "2");
        String.format("interfaces: %s\n", interfaces);
        String.format("interfaces: %s, count: %s", interfaces, "2");
      new Formatter().format("interfaces: %s\n", interfaces);
        new PrintWriter(System.out).format("interfaces: %s\n", interfaces);
    }

    private static String wrap3arg2( String format, Object[] formatArguments ) {
        Formatter formatter = new Formatter();
        formatter.format( null, format, formatArguments ); // formatArguments triggers inspection, quickfix changes the semantics
        return formatter.toString();
    }

    private String bar(int[] array) {
        return String.valueOf(array) + array.toString();
    }
}
