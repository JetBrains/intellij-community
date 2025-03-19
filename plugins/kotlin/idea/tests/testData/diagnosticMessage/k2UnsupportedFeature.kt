// !DIAGNOSTICS: UNSUPPORTED_FEATURE
// LANGUAGE_VERSION: 2.1
// !DIAGNOSTICS_NUMBER: 3

fun multiDollar() {
    $$""
}

fun whenGuards(n: Number) {
    when (number) {
        is Int if number > 42 -> Unit
        else -> Unit
    }
}

fun nonLocalBreakContinue() {
    for (i in 0..1) {
        run {
            break
        }
    }
}
