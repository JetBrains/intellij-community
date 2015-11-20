package com.siyeh.igtest.bugs.empty_statement_body;

public class EmptyStatementBody
{
    private void foo(int j)
    {
        <warning descr="'while' statement has empty body">while</warning>(bar());
        <warning descr="'while' statement has empty body">while</warning>(bar()){

        }
        <warning descr="'for' statement has empty body">for</warning>(int i = 0;i<4;i++);
        <warning descr="'for' statement has empty body">for</warning>(int i = 0;i<4;i++)
        {

        }
        <warning descr="'if' statement has empty body">if</warning>(bar());
        <warning descr="'if' statement has empty body">if</warning>(bar()){

        }
        if(bar()){
            return;
        }
        <warning descr="'else' statement has empty body">else</warning>
        {

        }
        if(bar()){
            return;
        }
        <warning descr="'else' statement has empty body">else</warning>;

        <warning descr="'switch' statement has empty body">switch</warning> (j) {}
    }

    private boolean bar()
    {
        return true;
    }

    void comments(boolean b) {
        if (b); // comment
        while (b) {
            // comment
        }
        do {
            ; // comment
        } while (b);
        if (b) /*comment*/;
    }
}
