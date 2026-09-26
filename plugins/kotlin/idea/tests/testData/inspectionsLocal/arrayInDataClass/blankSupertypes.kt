// WITH_STDLIB

interface I1
interface I2
interface I3 : I2

open class C
open class D : C()

data class A(val <caret>a: IntArray): D(), I1, I3