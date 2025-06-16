// REGISTRY: ide.completion.command.force.enabled true

class Foo

class Bar

operator fun Foo.rangeTo(bar: Bar): IntRange = IntRange.EMPTY

class Baz

operator fun Foo.rangeTo(baz: Baz): LongRange = LongRange.EMPTY

fun test() {
    val bar = Bar()
    val baz = Baz()

    Foo()..<caret>
}

// EXIST: bar, baz