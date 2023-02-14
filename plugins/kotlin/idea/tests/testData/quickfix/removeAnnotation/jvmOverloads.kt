// "Remove @JvmOverloads annotation" "true"
// WITH_STDLIB

interface T {
    @kotlin.jvm.<caret>JvmOverloads fun foo(s: String = "OK")
}
