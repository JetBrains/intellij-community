package com.siyeh.igtest.bugs;

public class DuplicateCondition {
    public void foo()
    {
        if(bar()||bar())
        {
            System.out.println("1");
        }else if(bar()|| true)
        {
            System.out.println("2");
        }
    }

    public boolean bar()
    {
        return true;
    }
}
