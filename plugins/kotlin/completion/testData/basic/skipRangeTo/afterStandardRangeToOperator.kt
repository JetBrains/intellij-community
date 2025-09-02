// REGISTRY: ide.completion.command.force.enabled true

class Foo : Comparable<Foo> {

    override fun compareTo(other: Foo): Int = 0
}

fun test() {
    Foo() ..<caret>
}

// EXIST: null