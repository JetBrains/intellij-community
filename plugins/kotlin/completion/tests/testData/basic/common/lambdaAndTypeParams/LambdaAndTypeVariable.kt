// FIR_COMPARISON
// FIR_IDENTICAL
fun test() {
    buildList { // this: MutableList<out Any?>
        val foo = this // MutableList<TypeVariable<E>>
        foo.add<caret>
    }
}

// EXIST: {"lookupString":"add","tailText":"(element: E)","typeText":"Boolean"}
// EXIST: {"lookupString":"add","tailText":"(index: Int, element: E)","typeText":"Unit"}
// EXIST: {"lookupString":"addAll","tailText":"(elements: Collection<E>)","typeText":"Boolean"}
// EXIST: {"lookupString":"addAll","tailText":"(index: Int, elements: Collection<E>)","typeText":"Boolean"}