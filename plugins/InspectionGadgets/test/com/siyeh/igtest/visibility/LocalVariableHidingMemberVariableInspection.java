package com.siyeh.igtest.visibility;

import com.siyeh.igtest.visibility2.DifferentPackageClass;


public class LocalVariableHidingMemberVariableInspection  extends DifferentPackageClass
{
    private int m_barangus = -1;

    public LocalVariableHidingMemberVariableInspection(int barangus)
    {
        m_barangus = barangus;
    }

    public void foo()
    {
        int fooBar;
        final Object m_barangus = new Object();
        System.out.println("bar" + m_barangus);
    }

    public void setBarangus(int barangus)
    {
        m_barangus = barangus;
        System.out.println(m_barangus);
    }
}
