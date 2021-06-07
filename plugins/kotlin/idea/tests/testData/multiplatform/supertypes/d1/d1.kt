@file:Suppress("NO_ACTUAL_FOR_EXPECT")

package foo

expect interface <!LINE_MARKER("descr='Is subclassed by A  Click or press ... to navigate'")!>Supertype<!>

class A : Supertype
