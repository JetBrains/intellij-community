class WrongOrderedParamTags {
  <warning descr="'@param' tags are not in the right order">/**</warning>
   * @param a
   * @param <X>
   * @param b
   * @param <Y>
   * @param c
   * @param <Z>
   */
  <X, Y, Z> Z foo1(X a, Y b, Z c) {return null;}

  <warning descr="'@param' tags are not in the right order">/**</warning>
   * @param <X>
   * @param <Y>
   * @param <Z>
   * @param a
   * @param b
   * @param c
   */
  <X, Y, Z> Z foo2(X a, Y b, Z c) {return null;}

   <warning descr="'@param' tags are not in the right order">/**</warning>
   * @param c
   * @param a
   * @param b
   * @param <X>
   * @param <Y>
   * @param <Z>
   */
  <X, Y, Z> Z foo3(X a, Y b, Z c) {return null;}
}

<warning descr="'@param' tags are not in the right order">/**</warning>
 * @param <X>
 * @param <Z>
 * @param <Y>
 */
class Foo1<X, Y, Z> {}

<warning descr="'@param' tags are not in the right order">/**</warning>
 * @param <Z>
 * @param <X>
 * @param <Y>
 */
interface Foo2<X, Y, Z> {}