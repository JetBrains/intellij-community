// FIR_COMPARISON

fun foo() =
    object {
        <error>val x</error> = 1
        <error>fun getX()</error> = 1
    }