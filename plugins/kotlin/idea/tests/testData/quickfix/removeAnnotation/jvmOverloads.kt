// "Remove @JvmOverloads annotation" "true"
// WITH_STDLIB

interface T {
    @kotlin.jvm.<caret>JvmOverloads fun foo(s: String = "OK")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix