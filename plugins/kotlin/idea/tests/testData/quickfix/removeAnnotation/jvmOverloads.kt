// "Remove @JvmOverloads annotation" "true"
// WITH_STDLIB
// K2_ERROR: OVERLOADS_INTERFACE

interface T {
    @kotlin.jvm.<caret>JvmOverloads fun foo(s: String = "OK")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix