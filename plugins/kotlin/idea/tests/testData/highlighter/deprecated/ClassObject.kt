fun test() {
   <warning descr="[DEPRECATION]">MyClass</warning>.test
   MyClass()
   val a: MyClass? = null
   val b: MyInterface? = null
   <warning descr="[DEPRECATION]">MyInterface</warning>.test
   MyInterface.<warning descr="[DEPRECATION]">Companion</warning>
   <warning descr="[DEPRECATION]">MyInterface</warning>
   MyClass.<warning descr="[DEPRECATION]">Companion</warning>
   MyClass.<warning descr="[DEPRECATION]">Companion</warning>.test

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
