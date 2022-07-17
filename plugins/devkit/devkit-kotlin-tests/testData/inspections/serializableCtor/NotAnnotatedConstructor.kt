import java.io.Serializable

class <warning descr="Non-default constructor should be annotated with @PropertyMapping">NotAnnotatedConstructor</warning>(val myString: String,
                                                                                                                           val myInteger: Int,
                                                                                                                           val myBoolean: Boolean) : Serializable {

  companion object {
    private const val serialVersionUID = 1L
  }
}
