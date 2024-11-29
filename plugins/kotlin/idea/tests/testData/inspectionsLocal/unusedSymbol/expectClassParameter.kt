// PROBLEM: none

//common
expect fun op(expectParameter: String)

//platform
actual fun op(actualParameter: String) {}


//common
expect class CtrParams23(expect<caret>Class: String) {}

//platform
actual class CtrParams23 actual constructor(actualClass: String) {}

expect class DatabaseConfiguration {
    public constructor()

    public constructor(config: Int)
}