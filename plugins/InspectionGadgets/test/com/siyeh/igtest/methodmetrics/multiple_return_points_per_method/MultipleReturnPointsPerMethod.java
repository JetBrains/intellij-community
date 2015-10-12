package com.siyeh.igtest.methodmetrics;

public class MultipleReturnPointsPerMethod
{
    public void <warning descr="'fooBar' has 2 return points">fooBar</warning>()
    {
        if(barangus())
        {
            return;
        }
    }

    private boolean <warning descr="'barangus' has 2 return points">barangus</warning>()
    {
        if(true)
        {
            return true;
        }
        return false;
    }

    public void methodOfNoReturn() {
        System.out.println();
        System.out.println();
        System.out.println();
    }
}
