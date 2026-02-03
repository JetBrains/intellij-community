// WITH_STDLIB
class A (var a: Boolean = false)

fun f (a1: A, a2: A) {
    a1.a = true
    a2.a = false
    if(!a1.a){
        println("ALIASED!")
    }
}

fun main(){
    val a1 = A()
    f(a1, a1)
}