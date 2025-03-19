// FIR_IDENTICAL
package child

import parent.Parent

class Impl : Parent() {
    <caret>
}

// MEMBER: "equals(other: Any?): Boolean"
// MEMBER: "hashCode(): Int"
// MEMBER: "toString(): String"
// MEMBER: "publicMethod(): Unit"
// MEMBER: "protectedMethod(): Unit"