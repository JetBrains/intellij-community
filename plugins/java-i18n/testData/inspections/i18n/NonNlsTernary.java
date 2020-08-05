import org.jetbrains.annotations.NonNls;

class Foo {
    void test(boolean b) {
        @NonNls String s = b ? "foo" : "bar";
        foo(s);
        String s2 = b ? <warning descr="Hardcoded string literal: \"foo\"">"foo"</warning> : <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>;
        foo(s2);
        
        @NonNls String s1;
        if (b) {
            s1 = "foo";
        } else {
            s1 = "bar";
        }
        foo(s1);
    }
    
    void foo(String s) {}
}