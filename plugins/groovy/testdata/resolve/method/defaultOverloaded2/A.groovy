class Overloaded extends HashMap {
    def plus(Map o1) {
        return this
    }

    def foo = {}
}

(new Overloaded() + new Overloaded()).<ref>foo()
