// "Change all usages of 'java.lang.Iterable<T>' in this file to a Kotlin class" "true"
// K2_ERROR: Conflicting import: imported name 'Iterable' is ambiguous.
// K2_ERROR: Conflicting import: imported name 'Iterable' is ambiguous.
import java.lang.*
import java.lang.Iterable
import java.lang.Iterable
import java.lang.Iterable as Foo

fun <T> a() : java.lang.Iterable<T>? {
    return null
}

fun b() : java.lang.Iterable<String>? {
    return null
}

fun c() : Foo<String><caret> {
    throw Exception()
}

fun d() : java.lang.Iterable<String>? {
    return null
}

fun e() : Iterable<String>? {
    throw Exception()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MapPlatformClassToKotlinFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.MapPlatformClassToKotlinFix