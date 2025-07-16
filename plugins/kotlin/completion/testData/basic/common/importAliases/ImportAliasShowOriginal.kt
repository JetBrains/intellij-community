import kotlin.concurrent.atomics.AtomicArray as AtomicArrayAliased

fun foo(): AtomicArra<caret> { }

// EXIST: AtomicArray
// EXIST: AtomicArrayAliased