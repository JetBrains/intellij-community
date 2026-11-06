// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: none
class Column

<caret>public inline companion val Column.bar: Int
    get() = 5
