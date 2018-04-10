import groovy.transform.CompileStatic

@CompileStatic
class Foo {
    void bar(List<Object> parameters) {
        def list = arguments()
        call(list[0], list[1] as Integer)
        call("", 1)
        call(parameters[0], parameters[1] as Integer)
    }

    static List arguments() {
        return ["1", 2]
    }

    void call(Object obj, Integer num) {
    }
}