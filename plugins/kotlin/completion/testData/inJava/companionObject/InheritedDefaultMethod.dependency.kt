package a

interface Base {
    fun inheritedDefault(): String = "hi"
}

class Test {
    companion object : Base
}
