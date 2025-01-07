// Issue: KT-65406
// PLATFORM: common
// FILE: common.kt

package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. Consider using the '-Xexpect-actual-classes' flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573">expect</warning>
class MyClass {
    val base: String
}

// PLATFORM: linux
// FILE: linux.kt

package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. Consider using the '-Xexpect-actual-classes' flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573">actual</warning>
class MyClass {
    actual val base: String = <error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'String', actual 'Int'.">42</error>
}

// PLATFORM: jvm
// FILE: jvm.kt

package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. Consider using the '-Xexpect-actual-classes' flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573">actual</warning>
class MyClass {
    actual val base: String = <error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'String', actual 'Int'.">42</error>
}

// PLATFORM: MinGW
// FILE: win.kt

package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. Consider using the '-Xexpect-actual-classes' flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573">actual</warning>
class MyClass {
    actual val base: String = <error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'String', actual 'Int'.">42</error>
}

// PLATFORM: js
// FILE: js.kt
package tm

<warning descr="[EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING] 'expect'/'actual' classes (including interfaces, objects, annotations, enums, and 'actual' typealiases) are in Beta. Consider using the '-Xexpect-actual-classes' flag to suppress this warning. Also see: https://youtrack.jetbrains.com/issue/KT-61573">actual</warning>
class MyClass {
    actual val base: String = <error descr="[INITIALIZER_TYPE_MISMATCH] Initializer type mismatch: expected 'String', actual 'Int'.">42</error>
}
