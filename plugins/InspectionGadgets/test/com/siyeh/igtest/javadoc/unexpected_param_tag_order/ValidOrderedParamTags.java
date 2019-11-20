class ValidOrderedParamTags {
  /**
   * @param nonExistingParameter
   */
  void foo1() {}

  /**
   * @param a
   * @param b
   * @param c
   * @param <X>
   * @param <Y>
   * @param <Z>
   */
  <X, Y, Z> Z foo2(X a, Y b, Z c) {return null;}

  /**
   */
  <X, Y, Z> Z foo3(X a, Y b, Z c) {return null;}
}

/**
 * @param <NON_EXISTING_GENERIC_PARAMETER>
 */
class Foo1 {}

/**
 * @param <X>
 * @param <Y>
 * @param <Z>
 */
class Foo2<X, Y, Z> {}

/**
 */
interface Foo3<X, Y, Z> {}