package com.siyeh.igtest.controlflow.if_statement_with_identical_branches;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {

    void one() {
        <warning descr="'if' statement with identical branches">if</warning> (true) {

        } else {

        }
        <warning descr="'if' statement with identical branches">if</warning> (false) {
            System.out.println();
            return;
        }
        System.out.println();
    }

    int two() {
        <warning descr="'if' statement with identical branches">if</warning> (true) {
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
            <warning descr="'if' statement with identical branches">if</warning> (false) {
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
            <warning descr="'if' statement with identical branches">if</warning> (false) {
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
        <warning descr="'if' statement with identical branches">if</warning> (true) {

        } else <warning descr="'if' statement with identical branches">if</warning> (true) {

        } else <warning descr="'if' statement with identical branches">if</warning> (true) {

        } else {

        }
    }

  void blocks() {
    <warning descr="'if' statement with identical branches">if</warning> (true) {
      System.out.println();
      return;
    }
    System.out.println();
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

  public static String calculate(int someNumber) {
    if (someNumber == 0 ) {
      try {
        return placeOrder(3, null);
      }
      catch( Exception e ) {
        System.out.println("e = " + e);
      }
    }
    else if (someNumber == 1) {
      try {
        return placeOrder(3, someNumber, null);
      }
      catch(Exception e ) {
        System.out.println("e = " + e);
      }
    }
    return null;
  }

  private static String placeOrder(int i, int someNumber, Object o) {
    return null;
  }

  private static String placeOrder(int i, Object o) {
    return null;
  }

  void m() {
    int j;
    <warning descr="'if' statement with identical branches">if</warning> (true) {
      j = 2;
    }
    else {
      j = 2;
    }
    System.out.println("j = " + j);
  }
}
