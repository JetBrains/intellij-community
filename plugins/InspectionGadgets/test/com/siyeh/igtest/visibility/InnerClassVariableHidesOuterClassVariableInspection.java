package com.siyeh.igtest.visibility;

import java.util.Set;
import java.util.HashSet;

public class InnerClassVariableHidesOuterClassVariableInspection
{
    private int m_barangus = -1;

    public void foo()
    {
        System.out.println(m_barangus);
    }

    private static class InnerClass
    {
        private int m_barangus = 3;

        public void foo()
        {
            System.out.println(m_barangus);
        }

    }
}
