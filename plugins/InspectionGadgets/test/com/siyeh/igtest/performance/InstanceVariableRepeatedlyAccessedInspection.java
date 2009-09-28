package com.siyeh.igtest.performance;

public class InstanceVariableRepeatedlyAccessedInspection
{
    private String m_value;

    public void fooBar()
    {
        m_value = "0";
        m_value = "0";
        m_value = "0";
    }
    public void fooBarangus()
    {
        m_value.toLowerCase();
        m_value.toLowerCase();
        m_value.toLowerCase();
    }

    public void fooBaz()
    {
        System.out.println(m_value);
        System.out.println(m_value);
        System.out.println(m_value);
    }
}
