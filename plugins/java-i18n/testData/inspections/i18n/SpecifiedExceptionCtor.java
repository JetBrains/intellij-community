import java.io.*;
class Foo {
    void foo(String s) throws IOException {
        if (s == null) {
            throw new NullPointerException(<warning descr="Hardcoded string literal: \"null expected here\"">"null expected here"</warning>)<EOLError descr="';' expected"></EOLError>
        }
        throw new IOException("ex");
    }
}

class MyEx extends IOException {
    MyEx() {
        this(<warning descr="Hardcoded string literal: \"my ex\"">"my ex"</warning>);
    }

    MyEx(String s) { 
        super("my ex");
    }
    
}