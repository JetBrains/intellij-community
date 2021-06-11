package test

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import org.junit.jupiter.params.provider.ValueSource

object PrivateClassRule {
  @MethodSource("parmeters")
  @Test
  fun testWithTestAnnotation() {}



  @ValueSource()
  @Test
  fun testWithTestAnnotationNoParameterized() {}
}