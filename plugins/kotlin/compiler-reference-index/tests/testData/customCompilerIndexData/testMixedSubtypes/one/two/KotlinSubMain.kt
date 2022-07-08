package main.another.one.two

import one.two.MainJava
import one.two.SubMainJavaClass

open class KotlinMain : MainJava() {
    open class NestedKotlinMain : MainJava()
    open class NestedKotlinNestedMain : MainJava.NestedJava()
    open class NestedKotlinSubMain : SubMainJavaClass()
}

object ObjectKotlin : SubMainJavaClass.SubNestedJava() {
    object NestedObjectKotlin {
        open class NestedNestedKotlin : SubMainJavaClass.SubNestedJava()
    }
}

open class K : MainJava.Wrapper.NestedWrapper()

typealias AliasToK = K

class KotlinFromAlias : AliasToK()