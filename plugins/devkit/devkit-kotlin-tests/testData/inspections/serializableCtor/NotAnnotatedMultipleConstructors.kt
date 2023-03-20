import java.io.Serializable

class <warning descr="Non-default constructor should be annotated with @PropertyMapping">NotAnnotatedMultipleConstructors</warning>(val myString: String,
                                       val myInteger: Int,
                                       val myBoolean: Boolean) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }

  <warning descr="Non-default constructor should be annotated with @PropertyMapping">constructor</warning>(string: String, integer: Int) : this(string, integer, false)

  <warning descr="Non-default constructor should be annotated with @PropertyMapping">constructor</warning>(string: String) : this(string, 0, false)

}
