package com.siyeh.igtest.controlflow.switch_statement_with_confusing_declaration;

public class SwitchStatementWithConfusingDeclaration
{
    public static void main(String[] args)
    {
        switch(3)
        {
            case 2:
                int <warning descr="Local variable 'x' declared in one 'switch' branch and used in another">x</warning> = 0;
                break;
            case 3:
                x = 3;
                System.out.println(x);
                break;
        }
    }

    enum E {FOO, BAR, BAZ}

    public int test(E e) {
        return switch (E.FOO) {
            default:
                int <warning descr="Local variable 'x' declared in one 'switch' branch and used in another">x</warning> = 1;
                System.out.println(x);
                break 1;
            case FOO:
            case BAR:
            case BAZ:
                x = 2;
                System.out.println(x);
                break 2;
        };
    }
}
