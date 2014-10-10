package com.siyeh.igtest.classlayout;

public class StaticInheritance implements <warning descr="Interface 'ConstantInterface' is implemented only for its 'static' constants">ConstantInterface</warning> {
    public void foo()
    {
        System.out.println(CONST1);
        System.out.println(ConstantInterface.CONST1);
        System.out.println(StaticInheritance.CONST1);
        System.out.println(new StaticInheritance().CONST1);
    }
}

class Class3  extends StaticInheritance
{
   public void foo()
   {
        System.out.println(CONST1);
        System.out.println(StaticInheritance.CONST1);
   }
}
class Class2
{
   public void foo()
   {
        System.out.println(StaticInheritance.CONST1);
        System.out.println(new StaticInheritance().CONST1);
   }
}
class Class4 implements <error descr="Class name expected">java.lang</error> {}
interface ConstantInterface
{
  int CONST1 = 0;
  int CONST2 = 0;
}
