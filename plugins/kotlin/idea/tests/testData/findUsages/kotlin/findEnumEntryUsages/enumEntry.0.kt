// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtEnumEntry
package one

enum class TargetEnumClass {
    ENT<caret>RY1,
    ENTRY2;
}

enum class AnotherEnumClass {
    ENTRY1,
    ANOTHER_ENTRY;
}

fun usage(entry: TargetEnumClass) {
    when (entry) {
        TargetEnumClass.ENTRY1 -> {}
        TargetEnumClass.ENTRY2 -> {}
    }
}

fun unrealtedUsage(entry: AnotherEnumClass) {
    when (entry) {
        AnotherEnumClass.ENTRY1 -> {}
        AnotherEnumClass.ANOTHER_ENTRY -> {}
    }
}
