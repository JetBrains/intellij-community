package com.siyeh.igtest.cloneable;

public class CloneDeclaresCloneNonSupportedExceptionInspection  implements Cloneable
{
    
    public void foo()
    {
        
    }
    
    public Object clone()
    {
        try
        {
            return super.clone();
        }
        catch(CloneNotSupportedException e)
        {
            return null;
        }
    }
}
