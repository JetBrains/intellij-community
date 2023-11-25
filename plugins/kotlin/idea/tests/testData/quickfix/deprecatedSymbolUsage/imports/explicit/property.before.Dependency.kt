package com.example

class Old {
    fun bar() = Unit
}

class New
fun New.bar() = Unit


@Deprecated(
    "message",
    ReplaceWith("newProp", "com.example.newProp", "com.example.bar"),
)
val oldProp = Old()

val newProp = New()
