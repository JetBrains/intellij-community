class DuplicatedParamTags {
  <warning descr="'@param' tags doesn't match parameter-declaration order">/**</warning>
   * @param a
   * @param b
   * @param b
   * @param c
   * @param <X>
   * @param <Y>
   * @param <Z>
   */
  <X, Y, Z> Z foo1(X a, Y b, Z c) {return null;}

  <warning descr="'@param' tags doesn't match parameter-declaration order">/**</warning>
   * @param a
   * @param b
   * @param c
   * @param <X>
   * @param <Y>
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
   * @param <Z>
   */
  <X, Y, Z> Z foo3(X a, Y b, Z c) {return null;}
}

<warning descr="'@param' tags doesn't match parameter-declaration order">/**</warning>
 * @param <X>
 * @param <X>
 * @param <Y>
 * @param <Z>
 */
class Foo1<X, Y, Z> {}

/**
 * @param <X>
 * @param <Y>
 * @param <Z>
 * @param <Z>
 */
interface Foo2<X, Y, Z> {}