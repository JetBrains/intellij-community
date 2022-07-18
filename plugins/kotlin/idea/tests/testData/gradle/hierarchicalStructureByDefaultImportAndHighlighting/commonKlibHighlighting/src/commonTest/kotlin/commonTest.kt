import <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: kotlinx'")!>kotlinx<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>serialization<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>Serializable<!>
import <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: kotlinx'")!>kotlinx<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>coroutines<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>CoroutineScope<!>
import <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: kotlinx'")!>kotlinx<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>coroutines<!>.<!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>launch<!>

@Suppress("unused_parameter")
private fun testSerializable(a: <!HIGHLIGHTING("severity='ERROR'; descr='[INVISIBLE_REFERENCE] Cannot access 'Serializable': it is internal in 'kotlin.io''")!>Serializable<!>) {
}

private fun <!HIGHLIGHTING("severity='ERROR'; descr='[UNRESOLVED_REFERENCE] Unresolved reference: CoroutineScope'")!>CoroutineScope<!>.testCoroutines() {
    <!HIGHLIGHTING("severity='ERROR'; descr='[DEBUG] Reference is not resolved to anything, but is not marked unresolved'")!>launch<!> {

    }
}
