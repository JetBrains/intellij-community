// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xwhen-guards
interface A
interface B : A
class C : B

fun f(a: A) {
    when (a) {
        is B if a !is C -> {}
        else -> {
            println(a as? C)
        }
    }
}

fun f1(a: A) {
    when(a) {
        is B if a is C -> {
            if (<warning descr="[USELESS_IS_CHECK] Check for instance is always 'true'.">a is C</warning>) {}
        }
        is B if <warning descr="[USELESS_IS_CHECK] Check for instance is always 'true'.">a is A</warning> -> {
            if (<warning descr="Condition 'a is C' is always false">a is C</warning>) {}
        }
        <warning descr="'when' branch is never reachable">is C</warning> -> {
            
        }
    }
}

fun main() {
    f(C())
}