// "Remove @JvmField annotation" "true"
// WITH_STDLIB
interface I {
    val x: Int
}

class C1 : I { <caret>@JvmField override val x: Int = 1 }
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix