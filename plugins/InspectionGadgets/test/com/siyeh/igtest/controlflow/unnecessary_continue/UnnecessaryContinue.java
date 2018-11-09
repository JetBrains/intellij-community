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