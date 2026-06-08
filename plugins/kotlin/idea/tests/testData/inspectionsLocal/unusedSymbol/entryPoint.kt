// PROBLEM: none


annotation class TemporaryTestingFakeEntryPointAnnotation {}
class C {
    @TemporaryTestingFakeEntryPointAnnotation
    fun f<caret>ooXXX() {}
}