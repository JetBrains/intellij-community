// COMPILER_ARGUMENTS: -Xexplicit-backing-fields
internal class ClassConsumer {
    private val _sealedClasses = mutableListOf<Int>()

    val allClasses: Collection<Int>
        field = mutableListOf<Int>()

    val sealedClasses: Collection<Int> get() = _sealedClasses<caret>
}