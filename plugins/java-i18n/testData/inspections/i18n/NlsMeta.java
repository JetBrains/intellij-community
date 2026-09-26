import org.jetbrains.annotations.Nls;

@Nls
@interface DialogTitle {}

@interface JustAnno {}

interface Foo {
    @DialogTitle String foo4();
}

class NlsMeta implements Foo {
    void foo1(@DialogTitle String title) {}
    void foo2(@JustAnno String title) {}
    void foo3(@DialogTitle @JustAnno String title) {}
    public String foo4() {
        return <warning descr="Hardcoded string literal: \"Hello\"">"Hello"</warning>;
    }

    void test() {
        foo1(<warning descr="Hardcoded string literal: \"Hello\"">"Hello"</warning>);
        foo2("Hello");
        foo3(<warning descr="Hardcoded string literal: \"Hello\"">"Hello"</warning>);
    }

}