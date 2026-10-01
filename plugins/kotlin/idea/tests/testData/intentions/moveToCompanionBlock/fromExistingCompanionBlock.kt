// COMPILER_ARGUMENTS: -Xcompanion-blocks
// IS_APPLICABLE: false

class Foo {
    companion {
        fun e<caret>xisting() {
        }
    }
}