// PROBLEM: none
class Wrapper {
    class Wrapper2 {
        enum class MyEnum {
            <caret>Bar, Baz;
        }
    }
}

fun main() {
    Wrapper.Wrapper2.MyEnum.valueOf("Bar")
}
