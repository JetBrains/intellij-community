package com.siyeh.igtest.j2me;

import java.util.Iterator;
import java.util.ListIterator;
import java.util.Enumeration;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MethodCallInLoopCondition {
    public void foo() {
        for (int i = 0; i < <warning descr="Call to method 'bar()' in loop condition">bar</warning>(); i++) {

        }
        while (<warning descr="Call to method 'bar()' in loop condition">bar</warning>() != 4) {
            foo();
        }

        do {
            foo();
        }
        while (<warning descr="Call to method 'bar()' in loop condition">bar</warning>() != 4);

    }

    private int bar() {
        return 3;
    }

    void a(Iterator<String> it) {
        while (it.hasNext()) {
            String s = it.next();
        }
    }

    void b(ListIterator<String> it) {
        while (it.hasPrevious()) {
            String s = it.previous();
        }
    }

    void c(Enumeration<String> e) {
        while (e.hasMoreElements()) {
            e.nextElement();
        }
    }

    void d (ResultSet rs) throws SQLException {
        while (rs.next()) {
            rs.getInt(1);
        }
    }
}
