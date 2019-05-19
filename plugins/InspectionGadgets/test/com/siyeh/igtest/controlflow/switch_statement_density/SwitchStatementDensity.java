package com.siyeh.igtest.controlflow.switch_statement_density;

public class SwitchStatementDensity
{
    private int m_bar;

    public SwitchStatementDensity()
    {
        m_bar = 0;
    }

    public void fooBar()
    {
        final int bar = m_bar;
        <warning descr="'switch' has too low of a branch density (19%)">switch</warning>(bar)
        {
            case 3:
            case 4:
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                break;
            case 6:
                System.out.println("4");
                break;
            default:
                break;
        }
        switch(bar) {}

    }

    public int switchExpression(int e) {
        return <warning descr="'switch' has too low of a branch density (11%)">switch</warning> (e) {
            case 1:
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                System.out.println(e);
                break 1;
            default:
                break 0;

        };
    }

    public void ruleBaseSwitch(String s) {
        <warning descr="'switch' has too low of a branch density (9%)">switch</warning> (s) {
            case "one" -> {
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
                System.out.println(1);
            }
            case "two" -> {
                System.out.println(2);
            }
        }
    }
}
