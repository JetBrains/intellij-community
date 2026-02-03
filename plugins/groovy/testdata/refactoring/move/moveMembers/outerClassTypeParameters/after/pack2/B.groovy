package pack2

import pack1.Outer;

public class B {
    public static void foo() {
        Outer<ArrayList>.Inner<List> x;
    }
}