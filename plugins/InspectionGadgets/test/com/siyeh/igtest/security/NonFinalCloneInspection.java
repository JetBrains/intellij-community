package com.siyeh.igtest.security;


public class NonFinalCloneInspection implements Cloneable{
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
