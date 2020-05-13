import org.jetbrains.annotations.Nls;

class NlsIndirect {
    native void use(@Nls String s);
    native void useOk(String s);
    
    void test(boolean flag, String input) {
        String s = <warning descr="Hardcoded string literal: \"foo\"">"foo"</warning> + <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>;
        use(s);
        s = "bar";
        useOk(s);
        
        String s1 = flag ? input : <warning descr="Hardcoded string literal: \"baz\"">"baz"</warning>;
        if (Math.random() > 0.5) {
            use(s1);
        } else {
            useOk(s1);
        }
        s1 = <warning descr="Hardcoded string literal: \"qux\"">"qux"</warning>;
        use(s1);
    }
    
    @Nls String testReturn() {
        String s = ((<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>));
        return s;
    }
}