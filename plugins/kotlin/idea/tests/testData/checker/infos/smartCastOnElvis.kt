// FIR_IDENTICAL

fun foo(s: String) = s.length

fun baz(s: String?, r: String?): Int {
    return foo(r ?: when {
        s != null -> <info><info>s</info></info>
        else -> ""
    })
}

fun bar(s: String?, r: String?): Int {
    return (r ?: when {
        s != null -> <info><info>s</info></info>
        else -> ""
    }).length
}
