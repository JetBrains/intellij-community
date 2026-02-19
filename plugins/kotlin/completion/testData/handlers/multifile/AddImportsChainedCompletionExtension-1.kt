// IGNORE_K1
package app

fun myView(modifier: MyModifier) { }

fun main() {
    myView(modifier = MyModifier.fooBarExtensi<caret>)
}