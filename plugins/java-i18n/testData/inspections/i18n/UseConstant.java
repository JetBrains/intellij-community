class X {
    static final String CONSTANT = <warning descr="Hardcoded string literal: \"Value\"">"Value"</warning>;
    
    void test() {
        use(CONSTANT);
    }
    
    void use(String c) {}
}