fun test() {
   <warning descr="[DEPRECATION] 'companion object of MyClass' is deprecated. Use A instead">MyClass</warning>.test
   MyClass()
   val a: MyClass? = null
   val b: MyInterface? = null
   <warning descr="[DEPRECATION] 'companion object of MyInterface' is deprecated. Use A instead">MyInterface</warning>.test
   MyInterface.<warning descr="[DEPRECATION] 'companion object of MyInterface' is deprecated. Use A instead">Companion</warning>
   <warning descr="[DEPRECATION] 'companion object of MyInterface' is deprecated. Use A instead">MyInterface</warning>
   MyClass.<warning descr="[DEPRECATION] 'companion object of MyClass' is deprecated. Use A instead">Companion</warning>
   MyClass.<warning descr="[DEPRECATION] 'companion object of MyClass' is deprecated. Use A instead">Companion</warning>.test

   a == b
}

class MyClass(): MyInterface {
    @Deprecated("Use A instead") companion object {
        val test: String = ""
    }
}

interface MyInterface {
    @Deprecated("Use A instead") companion object {
        val test: String = ""
    }
}

// NO_CHECK_INFOS
// NO_CHECK_WEAK_WARNINGS
