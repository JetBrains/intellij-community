package com.siyeh.igtest.verbose;

public class UnnecessaryContinueInspection {
    public UnnecessaryContinueInspection() {
        for (; ;) {
        continue;
        }
    }

    public void foo() {
        while (true)
            continue;
    }

    public void foo2() {
        while (true)
            if (true)
            {
                continue;
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
                if (true)
                {
                    continue;
                }
                System.out.println("");
            }
    }

}