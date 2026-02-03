// FIR_IDENTICAL
//KT-1602
import lib.ArrayFactory

public class Impl : ArrayFactory {
    <caret>
}

val array: Array<String> = emptyArray()

// MEMBER: "create(): Array"
// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"