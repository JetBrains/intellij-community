package com.siyeh.igtest.bugs;

public class ChainedMethodCall {
    private X baz =  new X().foo().bar();
    public void baz(){
        new X().<warning descr="Chained method call 'foo()'">foo</warning>().<warning descr="Chained method call 'bar()'">bar</warning>();
        (new X().<warning descr="Chained method call 'foo()'">foo</warning>()).<warning descr="Chained method call 'bar()'">bar</warning>();
        String s = new StringBuilder().append("x: ").append(new X()).append("y: ").append(new Y()).toString();
    }

    class X {
        public Y foo() {
            return new Y();
        }
    }

    class Y {
        public X bar() {
            return new X();
        }
    }
}
