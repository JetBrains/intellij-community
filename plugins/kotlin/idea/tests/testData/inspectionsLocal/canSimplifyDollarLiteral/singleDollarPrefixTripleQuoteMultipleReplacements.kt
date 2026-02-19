// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    $"""
        \$\$\$
        Foo${'$'}Bar
        ${'$'}${'$'}${'$'}
        ${'$'}${'$'}`Baz Boo`
        ${'$'}${'$'}{}
        ${'$'}${'$'}_Goo${'$'}${'$'}
        ${'$'}${'$'} Foo
        ${'$'}${'$'}${'$'}
<caret>${'$'}${'$'}"""
}