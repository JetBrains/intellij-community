interface Bar<T> { }

interface Base<T> { }

class Foo<T,U> implements Base<U> {
    void fo<caret>o(Bar<U> bar) { }
}
