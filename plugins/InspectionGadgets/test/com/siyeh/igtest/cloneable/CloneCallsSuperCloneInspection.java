package com.siyeh.igtest.cloneable;

public class CloneCallsSuperCloneInspection  implements Cloneable
{
    
    public void foo()
    {
        
    }

    public Object clone()
    {
        return this;
    }
}
