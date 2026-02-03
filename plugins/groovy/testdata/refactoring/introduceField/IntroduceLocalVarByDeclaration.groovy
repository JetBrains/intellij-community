class Foo {
    def bar = 3
    def foo() {
        print "a"

        <selection>def a = 4 - bar</selection>
        print a
        a++
        a-=4
        print a
    }
}