class Foo {
    void bar(List<Object> parameters) {
        call(*arguments())
        call(*["", 1])
        call(*parameters)
    }

    static List arguments() {
        return ["1", 2]
    }

    void call(Object obj, Integer num) {
    }
}