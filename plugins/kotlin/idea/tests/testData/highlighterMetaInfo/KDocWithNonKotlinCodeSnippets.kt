// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
// ALLOW_ERRORS
// HIGHLIGHT_WARNINGS
// TOOL: com.siyeh.ig.classlayout.FinalMethodInFinalClassInspection
/**
 * Broken block with warnings and errors:
 * ```java
 * private static final class Foo() {}
 *   private static final bar();
 *
 *   @kotlin.jvm.JvmOverloads()
 *   private static bar(int)
 * }
 * ```
 *
 * Valid block:
 * ```java
 * private static final class Foo {}
 * ```
 *
 * Broken block:
 * ```xml
 * Nothing here
 * ```
 *
 * Broken block:
 * ```python
 * I love Kotlin
 * ```
 *
 * Broken block:
 * ```json
 * I love kotlin
 * ```
 */
fun foo(){}

