class Foo {
    public static final String CONST = 'b'

    def foo() {
        print 'a' + CONST<caret> + 'c'
    }
}