package test

class SomeClass

typealias AliasedSomeClass = SomeClass

fun usage<caret>(param: AliasedSomeClass) = param