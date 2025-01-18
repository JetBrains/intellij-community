// WITH_STDLIB
// PROBLEM: none
// K2-ERROR: 'expect' and 'actual' declarations can be used only in multiplatform projects. Learn more about Kotlin Multiplatform: https://kotl.in/multiplatform-setup
public expect fun <T> lazy2(i<caret>nitializer: () -> T)