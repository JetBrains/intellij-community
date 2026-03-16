context(c: Context)
fun fooCtx() {}

context(ctx: Context)
fun example() {
    fooCtx(/*<# с = ctx#>*/)
}

