// FIX: Use property access syntax
// COMPILER_ARGUMENTS: -XXLanguage:+ReferencesToSyntheticJavaProperties

fun main() {
    val j = J()
    j.<caret>setX(j::<caret>getX)
}
