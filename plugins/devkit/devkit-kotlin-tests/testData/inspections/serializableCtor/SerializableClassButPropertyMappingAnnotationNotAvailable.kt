import java.io.Serializable

class SerializableClassButPropertyMappingAnnotationNotAvailable(val myString: String,
                                                                val myInteger: Int,
                                                                val myBoolean: Boolean) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}
