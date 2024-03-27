package com.example

@Deprecated(
    "message",
    ReplaceWith("New", "com.example.New", "com.example.bar"),
)
class Old {
    fun bar() = Unit
}

class New
fun New.bar() = Unit
