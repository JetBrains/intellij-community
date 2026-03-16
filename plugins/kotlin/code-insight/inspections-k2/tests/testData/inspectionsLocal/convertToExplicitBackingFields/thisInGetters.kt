// COMPILER_ARGUMENTS: -Xexplicit-backing-fields

class UserViewModel {
    private val _names: MutableSet<String>

    val x: Set<String>
        get() = this._names<caret>

    init {
        this._names = mutableSetOf("John, Jack")
    }

    fun updateName(newName: String) {
        this._names += newName
    }
}