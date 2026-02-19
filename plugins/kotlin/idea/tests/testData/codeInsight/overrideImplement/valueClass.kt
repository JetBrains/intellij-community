// FIR_IDENTICAL

interface Runnable {
    fun run()
}

@JvmInline
value <caret>class B(private val property: Int) : Runnable {

}

// MEMBER: "run(): Unit"