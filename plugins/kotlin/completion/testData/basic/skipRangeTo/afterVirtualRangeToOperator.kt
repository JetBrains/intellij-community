// REGISTRY: ide.completion.command.force.enabled true

class Foo {

    operator fun rangeTo(bar: Bar): IntRange = IntRange.EMPTY
}

class Bar

fun test() {
    val bar = Bar()

    Foo()..<caret>
}

// EXIST: bar