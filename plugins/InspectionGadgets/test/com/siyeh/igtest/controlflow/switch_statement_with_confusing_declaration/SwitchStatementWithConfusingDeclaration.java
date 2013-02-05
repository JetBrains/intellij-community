package com.siyeh.igtest.controlflow.switch_statement_with_confusing_declaration;

public class SwitchStatementWithConfusingDeclaration
{
    public static void main(String[] args)
    {
        switch(3)
        {
            case 2:
                int x = 0;
                break;
            case 3:
                x = 3;
                System.out.println(x);
                break;
        }
    }
}
