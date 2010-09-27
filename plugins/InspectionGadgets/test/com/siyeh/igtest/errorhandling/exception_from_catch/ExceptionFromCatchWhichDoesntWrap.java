package com.siyeh.igtest.errorhandling.exception_from_catch;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.sql.SQLException;

public class ExceptionFromCatchWhichDoesntWrap {
    public final Iterator<String> iterator() {
        try {
            doStuff();
        }
        catch (SQLException ex) {
            handleEx(ex);

            return new Iterator<String>() {
                public boolean hasNext() {
                    return false;
                }

                public String next() {
                    throw new NoSuchElementException();
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    private void doStuff() throws SQLException {
    }

    private void handleEx(SQLException ex) {
    }

    void bar() {
        try {
            System.out.println("");
        } catch (NullPointerException e) {
            throw new RuntimeException();
        }
    }

    void ignore() {
        try {
            System.out.println("");
        } catch (NullPointerException ignore) {
            throw new RuntimeException();
        }
    }

}
