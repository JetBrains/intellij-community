package com.siyeh.igtest.bugs;

public class EqualsUsesNonFinalVariableInspection implements Comparable
{
    private int foo = 0;
    private final int bar;

    public EqualsUsesNonFinalVariableInspection(int foo, int bar)
    {
        this.foo = foo;
        this.bar = bar;
    }


    public boolean equals(Object o)
    {
        if(this == o)
        {
            return true;
        }
        if(!(o instanceof EqualsUsesNonFinalVariableInspection))
        {
            return false;
        }

        final EqualsUsesNonFinalVariableInspection nonFinalFieldReferencedInEquals = (EqualsUsesNonFinalVariableInspection) o;

        if(bar != nonFinalFieldReferencedInEquals.bar)
        {
            return false;
        }
        if(foo != nonFinalFieldReferencedInEquals.foo)
        {
            return false;
        }

        return true;
    }

    public int hashCode()
    {
        int result;
        result = foo;
        result = 29 * result + bar;
        return result;
    }

    public int compareTo(Object o)
    {
        if(foo > ((EqualsUsesNonFinalVariableInspection)o).foo)
        {
            return 1;
        }
        if(foo < ((EqualsUsesNonFinalVariableInspection)o).foo)
        {
            return -1;
        }
        if(bar > ((EqualsUsesNonFinalVariableInspection)o).bar)
        {
            return 1;
        }
        if(bar < ((EqualsUsesNonFinalVariableInspection)o).bar)
        {
            return -1;
        }
        return 0;

    }
}
