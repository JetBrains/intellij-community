import org.jetbrains.annotations.NonNls;

class StringMethods {
    void testLength() {
        System.out.println("foo".length());
    }
    
    void test(@NonNls String foo) {
        if (foo.equals("foo")) {}
        if (foo.startsWith("foo")) {}
        if (foo.endsWith("foo")) {}
        if (foo.equalsIgnoreCase("foo")) {}
        if (foo.contains("foo")) {}
        if ("foo".equals(foo)) {}
        if ("foo".equalsIgnoreCase(foo)) {}
    }
    
    void test2() {
        String foo = getFoo();
        if (foo.equals("foo")) {}
        if (foo.startsWith("foo")) {}
        if (foo.endsWith("foo")) {}
        if (foo.equalsIgnoreCase("foo")) {}
        if (foo.contains("foo")) {}
        if ("foo".equals(foo)) {}
        if ("foo".equalsIgnoreCase(foo)) {}
    }
    
    void testBooleanOp() {
        String foo = getFoo();
        if (!foo.equals("foo")) {}
        if (foo.startsWith("foo") && foo.endsWith("foo")) {}
    }

    void testUnresolved() {
        String foo = <error descr="Cannot resolve method 'getUnresolved' in 'StringMethods'">getUnresolved</error>();
        if (foo.equals(<warning descr="Hardcoded string literal: \"foo\"">"foo"</warning>)) {
        }
    }
    
    void testPropagateThroughLowerCase() {
        if (getFoo().toLowerCase().equals("bar")) {}
        if (this.getFoo().toLowerCase().equals("bar")) {}
    }
    
    void test() {
        test("foo".trim());
    }
    
    @NonNls native String getFoo();
}