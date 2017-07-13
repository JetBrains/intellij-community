package com.siyeh.igtest.bugs.suspicious_indent_after_control_statement;

public class SuspiciousIndentAfterControlStatement {

    public static String suspicousIndent(int i, int j) {
        switch (i) {
            case 1:
                if (j  / 8 == 0)
                    return "x";
                    <warning descr="'case' statement has suspicious indentation">case</warning> 0:
                if (j % 2 == 0)
                    return "even";
                else
                    return "odd";
                // with a break; the next line is okay, without it it gets the warning "suspicous indentation"
            default:
                throw new IllegalArgumentException("unknown function " + i);
        }
    }

    void indent(int i) {
        if (i ==9)
            System.out.println("foo");
            <warning descr="'System.out.println(\"bar\")' statement has suspicious indentation">System.out.println("bar")</warning>;
        if (i == 10);
            <warning descr="'System.out.println(\"great\")' statement has suspicious indentation">System.out.println("great")</warning>;

    }
}