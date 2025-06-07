// IS_APPLICABLE: false

fun main() {
    val a = MyList("a") +<caret> MyList("b") + MyList("c")
}

class MyList(element: String) : List<String> by listOf(element)  {
    operator fun plus(other: MyList): MyList = MyList(this.toString() + other.toString())
}
