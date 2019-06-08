package com.siyeh.igtest.bugs;

import java.util.List;
import java.util.stream.*;

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

    List<String> streaming(List<String> list, Object[] foo) {
        Object[] objects = Stream.of(foo).distinct().toArray();
        Object[] objects2 = Stream.of(foo).parallel().distinct().toArray();
        return list.stream().filter(s -> s.length() < 3).collect(Collectors.toList());
    }
}
