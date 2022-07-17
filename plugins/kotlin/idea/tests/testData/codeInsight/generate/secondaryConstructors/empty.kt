// ACTION_CLASS: org.jetbrains.kotlin.idea.actions.generate.KotlinGenerateSecondaryConstructorAction
// WITH_STDLIB
class Foo {<caret>
    val x = 1
    val y: Int
    val z: Int get() = 3
    val u: Int by lazy { 4 }

    init {
        y = 2
    }

    fun foo() {

    }

    fun bar() {

    }
}