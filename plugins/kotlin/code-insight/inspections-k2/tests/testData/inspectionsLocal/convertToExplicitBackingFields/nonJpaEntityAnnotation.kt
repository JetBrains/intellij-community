// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
package com.example

annotation class Entity

@Entity
class Person {
    private val _firstName: String = "firstName"
    val firstName: CharSequence
        get() = _first<caret>Name
}
