import com.intellij.openapi.util.Pair

object UseCoupleTypeInConstant {
  private val ANY: <warning descr="Replace with 'Couple<String>'">Pa<caret>ir<String, String>?</warning> = null
}
