package com.siyeh.igtest.classlayout;

public class StaticInheritanceInspection implements ConstantInterface {
    public void foo()
    {
        System.out.println(CONST1);
        System.out.println(ConstantInterface.CONST1);
        System.out.println(StaticInheritanceInspection.CONST1);
        System.out.println(new StaticInheritanceInspection().CONST1);
    }
}

class Class3  extends StaticInheritanceInspection
{
   public void foo()
   {
        System.out.println(CONST1);
        System.out.println(StaticInheritanceInspection.CONST1);
   }
}
class Class2
{
   public void foo()
   {
        System.out.println(StaticInheritanceInspection.CONST1);
        System.out.println(new StaticInheritanceInspection().CONST1);
   }
}
