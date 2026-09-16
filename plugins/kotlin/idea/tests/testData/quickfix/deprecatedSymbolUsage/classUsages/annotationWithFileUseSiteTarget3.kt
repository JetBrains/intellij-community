// "Replace with 'OptIn(*markerClass)'" "true"
// WITH_STDLIB 1.7.0
// K2_ERROR: DEPRECATION_ERROR
@file:<caret>UseExperimental(Foo::class, Bar::class)

annotation class Foo
annotation class Bar
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix