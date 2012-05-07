package com.siyeh.igtest.methodmetrics;

public class NestingDepthInspection
{
    public void fooBar()
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
