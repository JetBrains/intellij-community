package com.siyeh.igtest.j2me.private_member_access_between_outer_and_inner_class;

public class Simple {
    
    private int i;

    private Simple() {}

    private void foo() {}
    
    class Inner {{
        new <warning descr="Access to private member of class 'Simple' requires synthetic accessor">Simple</warning>();
        System.out.println(<warning descr="Access to private member of class 'Simple' requires synthetic accessor">i</warning>);
        <warning descr="Access to private member of class 'Simple' requires synthetic accessor">foo</warning>();
    }}
}
class Other {
    void foo(Simple o) {
        System.out.println(o.<error descr="'i' has private access in 'com.siyeh.igtest.j2me.private_member_access_between_outer_and_inner_class.Simple'">i</error>);
    }
}