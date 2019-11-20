class MissingParamTags {
  <warning descr="'@param' tags are not in the right order">/**</warning>
  * @param a
  * @param b
  * @param c
  */
 <X, Y, Z> Z foo1(X a, Y b, Z c) {return null;}

   <warning descr="'@param' tags are not in the right order">/**</warning>
  * @param <X>
  * @param <Y>
  * @param <Z>
  */
 <X, Y, Z> Z foo2(X a, Y b, Z c) {return null;}

   <warning descr="'@param' tags are not in the right order">/**</warning>
  * @param a
  */
 <X, Y, Z> Z foo3(X a, Y b, Z c) {return null;}
}

<warning descr="'@param' tags are not in the right order">/**</warning>
 * @param <X>
 */
class Foo1<X, Y, Z> {}

<warning descr="'@param' tags are not in the right order">/**</warning>
 * @param <Z>
 */
interface Foo2<X, Y, Z> {}