class MissingParamTags {
  /**
  * @param a
  * @param b
  * @param c
  */
 <X, Y, Z> Z foo1(X a, Y b, Z c) {return null;}

  /**
   * @param a
   */
  <X, Y, Z> Z foo2(X a, Y b, Z c) {return null;}

   <warning descr="'@param' tags are not in the right order">/**</warning>
  * @param <X>
  * @param <Y>
  * @param <Z>
  */
 <X, Y, Z> Z foo3(X a, Y b, Z c) {return null;}

   <warning descr="'@param' tags are not in the right order">/**</warning>
  * @param b
  */
 <X, Y, Z> Z foo4(X a, Y b, Z c) {return null;}

  /**
   * @param a
   * @param b
   * @param c
   * @param <X>
   * @param <Y>
   */
  <X, Y, Z> Z foo5(X a, Y b, Z c) {return null;}
}

/**
 * @param <X>
 */
class Foo1<X, Y, Z> {}

<warning descr="'@param' tags are not in the right order">/**</warning>
 * @param <Z>
 */
interface Foo2<X, Y, Z> {}