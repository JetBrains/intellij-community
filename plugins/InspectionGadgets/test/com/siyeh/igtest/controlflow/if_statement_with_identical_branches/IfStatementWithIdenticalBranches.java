package com.siyeh.igtest.controlflow.if_statement_with_identical_branches;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {

    void one() {
        if (true) {

        } else {

        }
        if (false) {
            System.out.println();
            return;
        }
        System.out.println();
    }

    int two() { 
        if (true) {
            int i = 2;
            return i;
        } else {
            int j = 2;
            return j;
        }
    }

    int three() {
        if (true) {
            int i = 3;
            return i;
        } else {
            int j = 4;
            return j;
        }
    }

    void four() {
        if (true) {
            if (false) {
                System.out.println();
                return;
            }
        }
        System.out.println();
    }

    void five() {
        boolean b = true;
        while (b) {
            if (true) {
                System.out.println();
            }
        }
        System.out.println();
    }

    void six() {
        if (true) {
            if (false) {
                System.out.println();
                System.out.println();
                return;
            }
            System.out.println();
        }
        System.out.println();
    }

    void seven() {
        if (true) {
            System.out.println();
            return;
        } else if (true) {
            System.out.println("different");
            return;
        }
        System.out.println();
    }

    void eight() {
        if (true) {
            System.out.println();
        } else if (true) {
            System.out.println("different");
        } else {
            System.out.println();
        }
    }

    void nine() {
        if (true) {

        } else if (true) {

        } else if (true) {

        } else {

        }
    }
}

class NotADup {
    public String getElementDescription(Object element, Collection location) {
        if (location instanceof List) {
            if (element instanceof String) {
                return notNullize(element);
            }
        } else if (location instanceof Set) {
            if (element instanceof String) {
                return message((String)element);
            }
        }
        return null;
    }

    private String notNullize(Object element) {
        return null;
    }

    private String message(String element) {
        return null;
    }

}
