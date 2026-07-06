// LANGUAGE_VERSION: 1.9
// PROBLEM: none
// ERROR: Data object cannot have a custom implementation of 'equals' or 'hashCode'
// K2_ERROR: DATA_OBJECT_CUSTOM_EQUALS_OR_HASH_CODE
data object A {
    <caret>override fun hashCode() = super.hashCode()
}
