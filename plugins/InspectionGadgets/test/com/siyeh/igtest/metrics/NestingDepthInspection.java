package com.siyeh.igtest.metrics;

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
