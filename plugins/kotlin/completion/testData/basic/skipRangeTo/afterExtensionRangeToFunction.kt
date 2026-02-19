// REGISTRY: ide.completion.command.force.enabled true

class Foo

class Bar

fun Foo.rangeTo(bar: Bar): IntRange = IntRange.EMPTY

fun test() {
    val bar = Bar()

    Foo()..<caret>
}

// ABSENT: bar