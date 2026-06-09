package lambdas

import kotlinx.coroutines.runBlocking

class InlineLambdaIndirect {
    
    suspend fun startFoo() {
        foo()
    }
    
    fun foo() {
        val a = "bla"
        a.also { runBlocking {  } }
        a.forEach { runBlocking {  } }
        bar { runBlocking {  } }
        baz { runBlocking {  } }

    }

    inline fun bar(f: () -> Unit) {
        f()
    }

    fun baz(f: () -> Unit) {
        f()
    }
}
