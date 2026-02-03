@file:Suppress("NO_ACTUAL_FOR_EXPECT")

package foo

expect interface <!LINE_MARKER("descr='Is subclassed by A (foo) Press ... to navigate'")!>Supertype<!>

class A : Supertype
