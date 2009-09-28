package com.siyeh.igtest.cloneable;

public class CloneCallsConstructorInspection  implements Cloneable
{
    
    public void foo()
    {
        
    }
    
    public Object clone()
    {
        return new CloneCallsConstructorInspection();
    }
}
