package com.siyeh.igtest.bugs;

public class InfiniteLoopStatementInspection
{

    public InfiniteLoopStatementInspection()
    {
    }

    private void foo() throws Exception
    {

        try
        {
            for(; true;)
            {
            }
        }
        catch(Exception e)
        {

        }
        try
        {
            for(int i = 0;  true; i++)
            {
                if(bar())
                    return;
            }
        }
        catch(Exception e)
        {

        }
        try
        {
            while(true)
            {
                System.out.println("");
            }
        }
        catch(Exception e)
        {

        }
        try
        {
            while(bar())
            {
                if(bar())
                    return;
            }
        }
        catch(Exception e)
        {

        }
        try
        {
            while(bar())
            {
                System.out.println("");
            }
        }
        catch(Exception e)
        {

        }
        try
        {
            do
            {
                System.out.println("");
            }
            while(true);
        }
        catch(Exception e)
        {

        }

    }

    private boolean bar()
    {
        return true;
    }

    static void compute() {
        int i = 0;

        label:
        {
            while (true) { // not an infinite loop
                if (i == 100) {
                    break label;
                }
                i++;
            }
        }

        System.out.println("i = " + i);
    }
}