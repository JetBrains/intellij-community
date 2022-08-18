class ClassContainingSerialVersionUidFieldButIsNotSerializable(val myString: String, val myInteger: Int, val myBoolean: Boolean) {
  companion object {
    private const val serialVersionUID = 1L
  }
}
