class Foo implements Comparable<Foo> {

    def Foo next() {
        return null
    }

    def Foo previous() {
        return null
    }

    @Override
    int compareTo(Foo o) {
        <selection>return 0</selection>
    }
}

print new Foo()..new Foo()