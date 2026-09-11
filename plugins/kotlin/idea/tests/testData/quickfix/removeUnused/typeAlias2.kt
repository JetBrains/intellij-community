// "Safe delete 'Other'" "true"
import Other

enum class MyEnum {
    HELLO,
    WORLD
}

typealias Other<caret> = MyEnum

// IGNORE_K2