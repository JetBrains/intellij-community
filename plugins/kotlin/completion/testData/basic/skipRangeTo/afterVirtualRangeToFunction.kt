// REGISTRY: ide.completion.command.force.enabled true

class Foo {

    fun rangeTo(bar: Bar): IntRange = IntRange.EMPTY
}

class Bar

fun test() {
    val bar = Bar()

    Foo()..<caret>
}

// ABSENT: bar