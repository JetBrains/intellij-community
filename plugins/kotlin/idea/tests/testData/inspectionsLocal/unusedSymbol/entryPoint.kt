// PROBLEM: none
// IGNORE_K1

annotation class TemporaryTestingFakeEntryPointAnnotation {}
class C {
    @TemporaryTestingFakeEntryPointAnnotation
    fun f<caret>ooXXX() {}
}