// WITH_STDLIB
package com.example

import com.example.MyState.*

sealed interface MyState {
    sealed interface Good : MyState
    object Good1 : Good
    class Good2 : Good
    class Good3 : Good

    sealed interface Bad : MyState
    class Bad1 : Bad
    class Bad2 : Bad
    class Bad3 : Bad
}

fun example(myState: MyState) {
    val x = when (myState) {
        is Good, <warning descr="'when' branch is never reachable">is Good1</warning> -> 1
        is Bad, <warning descr="'when' branch is never reachable">is Bad2</warning> -> 2
    }
    println(x)
}