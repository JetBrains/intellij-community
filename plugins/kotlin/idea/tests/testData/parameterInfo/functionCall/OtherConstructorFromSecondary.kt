class B private constructor(p: Int) {
    constructor() : this(<caret>)
    protected constructor(s: String) : this()
}

