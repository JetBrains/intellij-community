// "Remove @JvmOverloads annotation" "true"
// IGNORE_FIR
// WITH_STDLIB

interface T {
    @kotlin.jvm.<caret>JvmOverloads fun foo(s: String = "OK")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.inspections.RemoveAnnotationFix