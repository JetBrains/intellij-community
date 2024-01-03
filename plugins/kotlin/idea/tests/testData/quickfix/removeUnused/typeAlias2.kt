// "Safe delete 'Other'" "true"
import Other

enum class MyEnum {
    HELLO,
    WORLD
}

typealias Other<caret> = MyEnum

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix
/* IGNORE_K2 */