// PROBLEM: none
// ERROR: Constructor must be private in sealed class
// COMPILER_ARGUMENTS: -XXLanguage:-SealedInterfaces
sealed class Foo {
    <caret>protected constructor()
}