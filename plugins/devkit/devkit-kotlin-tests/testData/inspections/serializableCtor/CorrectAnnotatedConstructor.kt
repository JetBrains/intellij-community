import com.intellij.serialization.PropertyMapping
import java.io.Serializable

class CorrectAnnotatedConstructor @PropertyMapping("myString", "myInteger", "myBoolean") constructor(val myString: String,
                                                                                                     val myInteger: Int,
                                                                                                     val myBoolean: Boolean) : Serializable {
  companion object {
    private const val serialVersionUID = 1L
  }
}