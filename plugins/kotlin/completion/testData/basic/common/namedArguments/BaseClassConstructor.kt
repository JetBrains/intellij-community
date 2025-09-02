// IGNORE_K1
abstract class Base(
    fooFirst: Int,
    fooSecond: Int,
    fooThird: Int,
)

class Child : Base(
    fooFirst = getInt(),
    <caret>
    fooThird = getInt()
)

fun getInt(): Int = 10

// EXIST: fooSecond =