package com.siyeh.igtest.classmetrics;

public class AnonymousClassComplexityInspection {
    private boolean bar;
    private boolean baz;
    private boolean bazoom;
    public void foo()
    {
        Runnable runnable = new Runnable() {
            public void run() {
                if(bar)
                {

                }else if(baz)
                {

                }else if(bazoom)
                {

                }
                foo();
            }
        };
    }
}
