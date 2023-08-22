import org.jetbrains.annotations.NonNls;

class Foo {
    void test(boolean b, String s0) {
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
        
        String s4 = b ? (s0 + (b ? "s1" : "s1") + s0) + s0 : s0;
        nonNls(s4 + s4);
    }
    
    void foo(String s) {}
    
    void nonNls(@NonNls String s) {}
}