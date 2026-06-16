package a

interface Base {
    val inheritedProp: String
        get() = "x"
}

class Test {
    companion object : Base
}
