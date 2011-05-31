class Abc {
    def foo() {
        bar(2)
        print(bar(3, s: 4))
        def s = "a"
        s.bar(4)
        print(s.bar(5))

        s.invokeMethod("anme", [])
    }
}