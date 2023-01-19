import bar.div

// "Import extension function 'Number.div'" "true"
// ERROR: None of the following functions can be called with the arguments supplied: <br>public final operator fun div(other: Byte): Int defined in kotlin.Int<br>public final operator fun div(other: Double): Double defined in kotlin.Int<br>public final operator fun div(other: Float): Float defined in kotlin.Int<br>public final operator fun div(other: Int): Int defined in kotlin.Int<br>public final operator fun div(other: Long): Long defined in kotlin.Int<br>public final operator fun div(other: Short): Int defined in kotlin.Int
fun foo() {
    2 <selection><caret></selection>/ ""
}

/* IGNORE_FIR */