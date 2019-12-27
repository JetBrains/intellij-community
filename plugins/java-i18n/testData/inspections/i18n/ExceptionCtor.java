import java.io.*;
class Foo {
    void foo(String s) throws IOException {
        throw new IOException("ex");
    }
}

class MyEx extends IOException {
    MyEx() {
        this("my ex");
    }

    MyEx(String s) { 
        super("my ex");
    }
    
}