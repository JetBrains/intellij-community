package com.siyeh.igtest.classlayout;


public abstract class AbstractMethodOverridesAbstractMethodInspection {
    public abstract Object foo() throws Exception;
}

 abstract class Child extends AbstractMethodOverridesAbstractMethodInspection
{
     public abstract Object foo() ;    
}
