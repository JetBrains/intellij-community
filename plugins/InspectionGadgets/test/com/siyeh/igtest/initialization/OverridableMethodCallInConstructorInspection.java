package com.siyeh.igtest.initialization;

public  class OverridableMethodCallInConstructorInspection
{

    protected OverridableMethodCallInConstructorInspection()
    {
        fooFinal();
        fooStatic();
        fooPrivate();
        fooOverridable();
        fooOverridden();
    }

    public void fooOverridden() {
    }

    public final void fooFinal()
    {

    }

    public static void fooStatic()
    {

    }

    private void fooPrivate()
    {

    }

    public void fooOverridable()
    {

    }
}
