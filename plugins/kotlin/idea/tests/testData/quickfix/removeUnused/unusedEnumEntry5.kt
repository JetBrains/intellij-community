// "Safe delete 'HELLO'" "true"
import MyEnum.HELLO

enum class MyEnum {
    HELLO<caret>,
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.SafeDeleteFix