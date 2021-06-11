package test

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import org.junit.jupiter.params.provider.ValueSource

class ParamT {
  @ParameterizedTest
  @MethodSource("<warning descr="Cannot resolve target method source: 'parmeters'">parmeters</warning>")
  @<warning descr="Suspicious combination '@Test' and '@ParameterizedTest'">Test</warning>
  fun testWithTestAnnotation(a : Int) {}



  @ValueSource()
  @<warning descr="Suspicious combination '@Test' and parameterized source">Test</warning>
  fun testWithTestAnnotationNoParameterized() {}

  fun parameters(): Array<String> {
    return arrayOf("a", "b")
  }
}