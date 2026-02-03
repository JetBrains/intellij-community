class Foo {
    void bar(List<Object> parameters) {
        def (a, b) = arguments()
        def (c,d) = ["", 1]
        def (Integer i, Thread t) = parameters
    }

    static List arguments() {
        return ["1", 2]
    }

    void call(Object obj, Integer num) {
    }
}