package com.siyeh.igtest.encapsulation.package_visible_inner_class;

public class PackageVisibleInnerClassInspection<T>
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
