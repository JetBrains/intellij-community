package com.siyeh.igtest.controlflow.unnecessary_continue;

public class UnnecessaryContinue {
    public UnnecessaryContinue() {
        for (; ;) {
        <warning descr="'continue' is unnecessary as the last statement in a loop">continue</warning>;
        }
    }

    public void foo() {
        while (true)
            <warning descr="'continue' is unnecessary as the last statement in a loop">continue</warning>;
    }

    public void foo2() {
        while (true)
            if (true)
            {
                <warning descr="'continue' is unnecessary as the last statement in a loop">continue</warning>;
            }
    }

    public void foo3() {
        while (true)
        {
            if (true)
            {
                continue;
            }
            System.out.println("foo");
        }
    }

    public void foo4() {
        while (true)
            try {
                if (true)
                {
                    continue;
                }
                System.out.println("");
            } finally {
            }
    }

    public void foo5() {
        while (true)
            synchronized(this) {
                if (false)
                {
                    continue;
                }
              else {
                  System.out.println();
                }
            }
    }

}


class Switch {
  enum E { A, B, C}
    void x(E e) {
      for (int i = 0; i < 10; i++)
        switch (e) {
            case A, B, C -> {
                <warning descr="'continue' is unnecessary as the last statement in a loop">continue</warning>;
            }
            default -> {
                <warning descr="'continue' is unnecessary as the last statement in a loop">continue</warning>;
            }
        }
    }

    void f(int n) {
        int a;
        for (int i = 0; i < 10; i++) {
            switch (n) {
                case 1 -> a = 0;
                default -> {
                    continue;
                }
            }
            System.out.println("a = " + a);
        }
    }

    void g(int n) {
        for (int i = 0; i < 10; i++) {
            switch (n) {
                case 1:
                    continue;
                default:
                <warning descr="'continue' is unnecessary as the last statement in a loop">continue</warning>;
            }
        }
    }
}