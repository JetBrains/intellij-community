import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    void bar(List<Object> parameters) {
        def list = arguments()
        def (a, b) = [list[0], list[1]]
        def (c,d) = ["", 1]
        def (Integer i, Thread t) = [(parameters[0] as Integer), (parameters[1] as Thread)]
    }

    static List arguments() {
        return ["1", 2]
    }

    void call(Object obj, Integer num) {
    }
}