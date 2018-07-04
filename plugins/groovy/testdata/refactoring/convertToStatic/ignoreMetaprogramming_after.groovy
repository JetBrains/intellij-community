import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    int field1

    void bar1() {
        this.field1
        this.bar2()
    }

    @CompileDynamic
    void bar2() {
        this.field2
    }

    @CompileDynamic
    void bar3() {
        this.bar4()
    }

    @CompileDynamic
    void bar5(String[] a) {
        a.bar6 {it.toUpperCase()}
    }

    void bar6(Object[] a, Closure<Object> c) {
    }
}
