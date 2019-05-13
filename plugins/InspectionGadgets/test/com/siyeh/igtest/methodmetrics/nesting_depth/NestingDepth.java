package com.siyeh.igtest.methodmetrics;

public class NestingDepth
{
    public void <warning descr="'fooBar' is overly nested (maximum nesting depth = 6)">fooBar</warning>()
    {
        if(bar())
        {
            if(bar())
            {
                if(bar())
                {
                    if(bar())
                    {
                        if(bar())
                        {
                            if(bar())
                            {

                            }
                        }
                    }
                }
            }
        }
    }

    private boolean bar()
    {
        return true;
    }

}
