// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: none
package org.example

class Column

<caret>public inline companion fun Column.bar(): Int = 5

fun example(): Int = Column.bar()
