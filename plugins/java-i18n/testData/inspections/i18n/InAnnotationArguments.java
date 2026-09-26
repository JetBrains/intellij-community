@interface Anno {
    String value();
}

@Anno(<warning descr="Hardcoded string literal: \"abcd\"">"abcd"</warning>)
class Test {
    @Anno(<warning descr="Hardcoded string literal: \"abcd\"">"abcd"</warning>)
    int field;
    
    @Anno(<warning descr="Hardcoded string literal: \"abcd\"">"abcd"</warning>)
    void m(@Anno(<warning descr="Hardcoded string literal: \"abcd\"">"abcd"</warning>) int i) {}
}