
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class ParameterizedTestsDemo {

  <warning descr="No sources are provided, the suite would be empty">@ParameterizedTest</warning>
  void testWithParamsNoSource(int i) { }

  @ParameterizedTest
  @MethodSource(value = {<warning descr="Method source 'a' must be static">"a"</warning>,
    <warning descr="Method source 'b' should have no parameters">"b"</warning>,
    <warning descr="Method source 'c' must have one of the following return type: Stream<?>, Iterator<?>, Iterable<?> or Object[]">"c"</warning>,
    "d"})
  void testWithParams(Object s) { }

  String[] a() {
    return new String[] {"a", "b"};
  }

  static String[] b(int i) {
    return new String[] {"a", "b"};
  }

  static Object c() {
    return new String[] {"a", "b"};
  }

  static Object[] d() {
    return new String[] {"a", "b"};
  }

  @ParameterizedTest
  @MethodSource(value = {<warning descr="Multiple parameters have to be wrapped in Arguments">"d"</warning>})
  void testWithMultipleParams(Object s, int i) { }

  @ParameterizedTest
  @EnumSource(<warning descr="No implicit conversion found to convert object of type E to int">E.class</warning>)
  void testWithEnumSource(int i) { }

  @ParameterizedTest
  @EnumSource(E.class)
  void testWithEnumSourceCorrect(E e) { }

  enum E {
    A, B;
  }

  @ParameterizedTest
  @ValueSource(ints = {1})
  void testWithValues(int i) { }

  @ParameterizedTest
  <warning descr="Exactly one type of input must be provided">@ValueSource(ints = {1},
    strings = "str")</warning>
  void testWithMultipleValues(int i) { }

  @ParameterizedTest
  <warning descr="No value source is defined">@ValueSource()</warning>
  void testWithNoValues(int i) { }

  @ParameterizedTest
  <warning descr="Multiple parameters are not supported by this source">@ValueSource(ints = 1)</warning>
  void testWithValuesMultipleParams(int i, int j) { }

  @ParameterizedTest
  @ValueSource(ints = {1})
  <warning descr="Suspicious combination @Test and @ParameterizedTest">@org.junit.jupiter.api.Test</warning>
  void testWithTestAnnotation(int i) { }

  @ValueSource(ints = {1})
  <warning descr="Suspicious combination @Test and parameterized source">@org.junit.jupiter.api.Test</warning>
  void testWithTestAnnotationNoParameterized(int i) { }

}
