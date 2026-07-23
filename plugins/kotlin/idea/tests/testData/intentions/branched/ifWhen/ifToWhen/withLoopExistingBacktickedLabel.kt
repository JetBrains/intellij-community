// COMPILER_ARGUMENTS: -XXLanguage:-AllowBreakAndContinueInsideWhen

fun test() {
    `foo bar`@ for (i in -2..2) {
        <caret>if (i > 0) i.hashCode()
        else continue
    }
}
