package com.siyeh.igtest.visibility;

import java.util.Set;
import java.util.HashSet;

public class FieldHidesSuperclassFieldInspection extends LocalVariableHidingMemberVariableInspection
{
    private int m_barangus = -1;

    public FieldHidesSuperclassFieldInspection(int barangus)
    {
        super(barangus);
    }

    public void foo()
    {
        System.out.println("bar" + m_barangus);
    }
}
