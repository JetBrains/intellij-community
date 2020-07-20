package com.siyeh.igtest.bugs.suspicious_indent_after_control_statement;

import java.util.*;

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

    public int get(Class baseClass) {
        // "interesting" parse tree.
        if (baseClass.equals(<error descr="')' expected"><error descr="Expression expected">.</error></error><error descr="')' expected"><error descr="Identifier expected">c</error></error>lass<error descr="Identifier expected"><error descr="Unexpected token">)</error></error><error descr="Unexpected token">)</error> {
            return 1;
        }
        return 0;
    }

    void m() {
        if (true)
            System.out.println();
            <warning descr="'class' statement has suspicious indentation">class</warning><error descr="Identifier expected"> </error> ;
    }

    class Lol {
        public void someMethod(String a, String b, Boolean c)
        {
            List<Integer> list = new ArrayList<>();
            if(a != null) {
                list.add(5);
            }
            else
            if (b != null) {
                list.add(4);
                if (c) {
                    list.add(3);
                }
            }
            else {
                list.add(2);
            }
            list.add(1);
            if (list != null) {
                list.add(0);
            }
        }
    }

    public String test(int test) {
        int random = new Random().nextInt();
        switch (test) {
            case 0:
                return "0";
            case 1:
                if (random < 0) {
                    return "-1";
                } else if (random > 0) {
                    return "+1";
                } else {
                    return null;
                }
            case 2:  // <--- inspection error on this line
            default:
                return "";
        }
    }
}