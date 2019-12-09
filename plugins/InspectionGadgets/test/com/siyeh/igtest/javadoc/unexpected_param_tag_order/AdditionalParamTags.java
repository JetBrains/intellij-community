class AdditionalParamTags {

  /**
   * @param a
   * @param b
   * @param c
   * @param <X>
   * @param <Y>
   * @param <Z>
   * @param <NON_EXISTING_GENERIC_PARAMETER>
   */
  <X, Y, Z> Z foo1(X a, Y b, Z c) {return null;}

  <warning descr="'@param' tags doesn't match parameter-declaration order">/**</warning>
   * @param a
   * @param b
   * @param c
   * @param nonExistingParameter
   * @param <X>
   * @param <Y>
   * @param <Z>
   */
  <X, Y, Z> Z foo2(X a, Y b, Z c) {return null;}

  /**
   * @param a
   * @param b
   * @param c
   * @param <X>
   * @param <Y>
   * @param <Z>
   * @param
   */
  <X, Y, Z> Z foo3(X a, Y b, Z c) {return null;}
}

/**
 * @param <X>
 * @param <Y>
 * @param <Z>
 * @param <NON_EXISTING_GENERIC_PARAMETER>
 */
class Foo1<X, Y, Z> {}

<warning descr="'@param' tags doesn't match parameter-declaration order">/**</warning>
 * @param <NON_EXISTING_GENERIC_PARAMETER>
 * @param <X>
 * @param <Y>
 * @param <Z>
 */
interface Foo2<X, Y, Z> {}