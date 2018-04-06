import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    private String field1 = bar1()

    static String bar1() {
        ""
    }

    void bar2() {
        field1.toUpperCase()
        bar1().toUpperCase()
    }
}
