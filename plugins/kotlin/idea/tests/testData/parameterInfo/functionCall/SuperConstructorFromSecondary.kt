open class A(x: Int) {
    protected constructor() : this(1) {}
    private constructor(p: String) : this(2) {}
}

class B : A {
    constructor() : super(<caret>)
}

