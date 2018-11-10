@interface Anno {
    String value();
}

@Anno(<warning descr="Hard coded string literal: \"abcd\"">"abcd"</warning>)
class Test {
    @Anno(<warning descr="Hard coded string literal: \"abcd\"">"abcd"</warning>)
    int field;
    
    @Anno(<warning descr="Hard coded string literal: \"abcd\"">"abcd"</warning>)
    void m(@Anno(<warning descr="Hard coded string literal: \"abcd\"">"abcd"</warning>) int i) {}
}