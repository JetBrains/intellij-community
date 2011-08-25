class Aaa {
    Aaa() {
        this(1)
    }

    <error descr="Recursive constructor invocation">Aaa(x)</error> {
        this(1, 2)
    }

    <error descr="Recursive constructor invocation">Aaa(x, y)</error> {
        this(1, 2, 3)
    }

    <error descr="Recursive constructor invocation">Aaa(x, y, z)</error> {
        this(1)
    }
}
