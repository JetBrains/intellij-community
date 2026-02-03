// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    $$"\r\n\$\\\t\b \$foo"<caret>
}