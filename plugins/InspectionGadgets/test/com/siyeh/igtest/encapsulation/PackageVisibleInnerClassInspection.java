package com.siyeh.igtest.encapsulation;

import java.util.Set;
import java.util.HashSet;

public class PackageVisibleInnerClassInspection
{
    class Barangus
    {

        public Barangus(int val)
        {
            this.val = val;
        }

        int val = -1;
    }

}
