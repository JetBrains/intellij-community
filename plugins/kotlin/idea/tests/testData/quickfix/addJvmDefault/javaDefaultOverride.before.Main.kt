// "Add '@JvmDefault' annotation" "true"
// COMPILER_ARGUMENTS: -Xjvm-default=enable
// WITH_STDLIB
interface Bar : Foo {
    <caret>override fun foo() {

    }
}
