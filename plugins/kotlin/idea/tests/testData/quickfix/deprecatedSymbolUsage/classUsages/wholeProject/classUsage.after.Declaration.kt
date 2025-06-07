package dependency

@Deprecated("", ReplaceWith("dependency.NewFoo"))
open class OldFoo

open class NewFoo()

fun foo(): List<NewFoo> = TODO()
