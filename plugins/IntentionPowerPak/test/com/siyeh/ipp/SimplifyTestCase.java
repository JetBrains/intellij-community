package com.siyeh.ipp;

import java.io.IOException;

public class SimplifyTestCase{
    private int x;

    public void foo(Exception e)
    {
        if(e instanceof IOException == false){
            //
            int y = getX();
        }
    }

    public int getX()
    {
        return x;
    }
}
