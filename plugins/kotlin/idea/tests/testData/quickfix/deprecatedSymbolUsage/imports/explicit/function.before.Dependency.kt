package com.example

class Old {
    fun bar() = Unit
}

class New
fun New.bar() = Unit


@Deprecated(
    "message",
    ReplaceWith("newFun()", "com.example.newFun", "com.example.bar"),
)
fun oldFun() = Old()

fun newFun() = New()
