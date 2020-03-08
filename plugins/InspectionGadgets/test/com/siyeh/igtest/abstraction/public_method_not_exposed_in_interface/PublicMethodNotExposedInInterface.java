package com.siyeh.igtest.abstraction;

public class PublicMethodNotExposedInInterface implements Interface {
    public void <warning descr="'public' method 'foo()' is not exposed via an interface">foo</warning>() {

    }

    public void baz() {
        bar2();
    }

    public static void bar() {

    }

    public void <warning descr="'public' method 'test()' is not exposed via an interface">test</warning>() {
         <error descr="Cannot resolve method 'fail' in 'PublicMethodNotExposedInInterface'">fail</error>();
    }

    private void bar2() {

    }

}
interface Interface {
    void baz();
}

