// "Remove 'private' modifier" "true"

sealed class A(x: Int) {
    private constructor(y: Double) : this(y.toInt())
    private constructor(y: String) : this(y.length)
    private constructor(y: Long) : this(y.toInt())
}

class B : A<caret>("")