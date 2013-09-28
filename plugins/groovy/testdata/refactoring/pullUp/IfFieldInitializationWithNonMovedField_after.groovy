public class A {
    protected final String f
}

class B extends A {
    final String foo;

    B(String fi, String foo) {
        this.foo = foo;
        if (fi == this.foo) {
            f = foo;
        } else {
            f = "";
        }
    }


}
