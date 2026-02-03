package testData.libraries
public class ClassWithConstructor(val a: String, b: Any) {
  constructor(a: String): this(a, a)
}