package lambdas

import kotlinx.coroutines.runBlocking

class InlineLambdaIndirect2 {

    suspend fun foo() {
        val a = "bla"
        a.also { indirectRunBlocking1() }
        a.forEach { indirectRunBlocking2() }
        bar { indirectRunBlocking3() }
        baz { indirectRunBlocking4() }
    }

    inline fun bar(f: () -> Unit) {
        f()
    }

    fun baz(f: () -> Unit) {
        f()
    }
    
    fun indirectRunBlocking1 () {
        runBlocking {  }
    }
    
    fun indirectRunBlocking2 () {
        runBlocking {  }
    }
    
    fun indirectRunBlocking3 () {
        runBlocking {  }
    }
    
    fun indirectRunBlocking4 () {
        runBlocking {  }
    }
}
