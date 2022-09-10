// ALLOW_AST_ACCESS
package test

interface KotlinInterface {
    @JvmDefault
    fun bar() {

    }

}


abstract class KotlinClass : KotlinInterface {

}