package com.siyeh.igtest.metrics;

public class ThreeNegationsPerMethodInspection
{
    int foo, bar, baz;

    public void okayMethod()
    {
        if(!!!true)
        {
            return;
        }
    }

    public void badMethod()
    {
        if(!!!!true)
        {
            return;
        }
    }

    public void badMethod2()
    {
        if(!!!true && 3 !=4)
        {
            return;
        }
    }

    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ThreeNegationsPerMethodInspection threeNegationsPerMethodInspection = (ThreeNegationsPerMethodInspection) o;

        if (bar != threeNegationsPerMethodInspection.bar) return false;
        if (baz != threeNegationsPerMethodInspection.baz) return false;
        if (foo != threeNegationsPerMethodInspection.foo) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = foo;
        result = 29 * result + bar;
        result = 29 * result + baz;
        return result;
    }
}
