// INTENTION_TEXT: "Import members from 'kotlin.LazyThreadSafetyMode'"
// WITH_RUNTIME

class A {
    val v1: Int by lazy(<caret>LazyThreadSafetyMode.NONE) { 1 }
    val v2: Int by lazy(LazyThreadSafetyMode.PUBLICATION) { 1 }
/*
    val v3 = LazyThreadSafetyMode.values
    val v4 = LazyThreadSafetyMode.valueOf("")
*/
}