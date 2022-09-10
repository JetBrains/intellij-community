// ALLOW_AST_ACCESS
package test

interface KotlinInterface {
    fun bar() {

    }

    fun f()
}


abstract class KotlinClass : KotlinInterface {
    override fun f() {

    }
}