// PROBLEM: none

private abstract class Base(@Suppress("unused") parent: String?) {
    <caret>constructor(parent: String?, @Suppress("unused") js: Any): this(parent)
}

@Suppress("unused")
private class Impl: Base/*("", "js")*/ {
    constructor(parent: String, js: Object): super(parent, js)
}