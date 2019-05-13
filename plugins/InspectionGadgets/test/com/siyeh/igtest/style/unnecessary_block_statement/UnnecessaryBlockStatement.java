package com.siyeh.igtest.style.unnecessary_block_statement;

public class UnnecessaryBlockStatement {
    public static void main(String[] args) {
        <warning descr="Braces around this statement are unnecessary">{</warning>
             System.out.println("3");
        }
        {
            int a;
        }
        <warning descr="Braces around this statement are unnecessary">{</warning>
            int b;
        }
        <warning descr="Braces around this statement are unnecessary">{</warning>
            <warning descr="Braces around this statement are unnecessary">{</warning>
            int a;
            }
        }
    }

    void oldSwitcharoo() {
        switch (5) {
            case 1: {
                int x = 0;
                break;
            }
            case 2: <warning descr="Braces around this statement are unnecessary">{</warning>
                int x = 0;
                break;
            }
        }
    }

    void ifThenElse() {
        {
            int i = 0;
        }
        if (true) {
            int i = 0;
        }
        <warning descr="Braces around this statement are unnecessary">{</warning>
            int i = 0;
        }
    }

    String f;
    void field() {
        <warning descr="Braces around this statement are unnecessary">{</warning>
            String f;
        }
        System.out.println();
    }
}
