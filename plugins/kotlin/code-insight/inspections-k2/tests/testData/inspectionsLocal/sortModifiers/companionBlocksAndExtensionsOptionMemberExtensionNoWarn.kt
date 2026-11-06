// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: none
// K2_ERROR: WRONG_MODIFIER_TARGET
class Column

class Sample {
    <caret>public inline companion fun Column.bar(): Int = 5
}
