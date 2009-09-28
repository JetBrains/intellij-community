package com.siyeh.igtest.style.unnecessary_this;

public class UnnecessaryThisInspection
{
    private int m_foo;

    public void fooBar()
    {
        this.m_foo = 3;
    }

    class X {
        private int m_foo;

        public void fooBar() {};

        void foo() {
            UnnecessaryThisInspection.this.m_foo = 4;
            UnnecessaryThisInspection.this.fooBar();
        }
    }

    public void fooBaz( int m_foo)
    {
        this.m_foo = 3;
    }

    public void fooBarangus()
    {
        int m_foo;
        this.m_foo = 3;
    }

    public void fooBarzoom()
    {
        for(int m_foo = 0;m_foo<4; m_foo++)
        {
            this.m_foo = 3;
        }
        this.fooBar();
    }

    private Throwable throwable = null;

    public void method()
    {
        try
        {
        }
        catch (Throwable throwable)
        {
            this.throwable = throwable;
            throwable.printStackTrace();
        }
    }
}
