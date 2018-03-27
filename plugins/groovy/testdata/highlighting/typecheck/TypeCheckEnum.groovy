import groovy.transform.CompileStatic

@CompileStatic
class FooEnum {

  static enum E {
    F, g, h
  }

  void explicitVoid() {}

  def s = ""

  def fooEnum(E s) {}

  def enumCast() {
    (E) null
    <error descr="Cannot cast 'char' to 'E'">(E) 1 as char</error>
    <error descr="Cannot cast 'BigDecimal' to 'E'">(E) 1 as BigDecimal</error>
    <error descr="Cannot cast 'BigInteger' to 'E'">(E) 1 as BigInteger</error>
    <error descr="Cannot cast 'double' to 'E'">(E) 1 as double</error>
    <error descr="Cannot cast 'float' to 'E'">(E) 1 as float</error>
    <error descr="Cannot cast 'int' to 'E'">(E) 1 as int</error>
    <error descr="Cannot cast 'short' to 'E'">(E) 1 as short</error>
    <error descr="Cannot cast 'long' to 'E'">(E) 1 as long</error>
    <error descr="Cannot cast 'boolean' to 'E'">(E) 1 as boolean</error>
    <error descr="Cannot cast 'void' to 'E'">(E) 1 as void</error>
    <error descr="Cannot cast 'void' to 'E'">(E) explicitVoid()</error>
    <error descr="Cannot cast 'Date' to 'E'">(E) new Date()</error>
    <error descr="Cannot cast 'String' to 'E'">(E) "a"</error>
    <error descr="Cannot cast 'GString' to 'E'">(E) "${System.in.read()}"</error>
    (E) new Object()
    <error descr="Cannot cast 'Object[]' to 'E'">(E) new Object[0]</error>
    <error descr="Cannot cast 'Closure<Integer>' to 'E'">(E) { int a, int b -> a + b }</error>
    (E) s
    <error descr="Cannot cast 'Matcher' to 'E'">(E) "aaa" =~ /aaa/</error>
    <error descr="Cannot cast 'List' to 'E'">(E) []</error>
    <error descr="Cannot cast 'String' to 'E'">(E) "F"</error>
  }

  def enumAssignment() {
    E e
    e = null
    <error descr="Cannot assign 'char' to 'E'">e</error> = 1 as char
    <error descr="Cannot assign 'BigDecimal' to 'E'">e</error> = 1 as BigDecimal
    <error descr="Cannot assign 'BigInteger' to 'E'">e</error> = 1 as BigInteger
    <error descr="Cannot assign 'double' to 'E'">e</error> = 1 as double
    <error descr="Cannot assign 'float' to 'E'">e</error> = 1 as float
    <error descr="Cannot assign 'int' to 'E'">e</error> = 1 as int
    <error descr="Cannot assign 'short' to 'E'">e</error> = 1 as short
    <error descr="Cannot assign 'long' to 'E'">e</error> = 1 as long
    <error descr="Cannot assign 'boolean' to 'E'">e</error> = 1 as boolean
    <error descr="Cannot assign 'void' to 'E'">e</error> = explicitVoid()
    <error descr="Cannot assign 'Date' to 'E'">e</error> = new Date()
    e = <warning descr="Cannot find enum constant 'a' in enum 'E'">"a"</warning>
    e = <warning descr="Cannot find enum constant 'abcdef' in enum 'E'">"abcdef"</warning>
    e = "F"
    e = <weak_warning descr="Cannot assign string to enum 'E'">"${System.in.read()}"</weak_warning>
    <error descr="Cannot assign 'Object' to 'E'">e</error> = new Object()
    <error descr="Cannot assign 'Object[]' to 'E'">e</error> = new Object[0]
    <error descr="Cannot assign 'Closure<Void>' to 'E'">e</error> = { int a, int t -> println(a + t) }
    <error descr="Cannot assign 'Object' to 'E'">e</error> = s
    <error descr="Cannot return 'Matcher' from method returning 'Object'"><error descr="Cannot assign 'Matcher' to 'E'">e</error> = "aaa" =~ /aaa/</error>
  }

  def enumVariable() {
    E e0 = null
    E <error descr="Cannot assign 'char' to 'E'">e1</error> = 1 as char
    E <error descr="Cannot assign 'BigDecimal' to 'E'">e2</error> = 1 as BigDecimal
    E <error descr="Cannot assign 'BigInteger' to 'E'">e3</error> = 1 as BigInteger
    E <error descr="Cannot assign 'double' to 'E'">e4</error> = 1 as double
    E <error descr="Cannot assign 'float' to 'E'">e5</error> = 1 as float
    E <error descr="Cannot assign 'int' to 'E'">e6</error> = 1 as int
    E <error descr="Cannot assign 'short' to 'E'">e7</error> = 1 as short
    E <error descr="Cannot assign 'long' to 'E'">e8</error> = 1 as long
    E <error descr="Cannot assign 'boolean' to 'E'">e9</error> = 1 as boolean
    E <error descr="Cannot assign 'void' to 'E'">e10</error> = explicitVoid()
    E <error descr="Cannot assign 'Date' to 'E'">e11</error> = new Date()
    E e12 = <warning descr="Cannot find enum constant 'a' in enum 'E'">"a"</warning>
    E e13 = <weak_warning descr="Cannot assign string to enum 'E'">"${System.in.read()}"</weak_warning>
    E <error descr="Cannot assign 'Object' to 'E'">e14</error> = new Object()
    E <error descr="Cannot assign 'Object[]' to 'E'">e15</error> = new Object[0]
    E <error descr="Cannot assign 'Closure<Void>' to 'E'">e16</error> = { int a, int t -> println(a + t) }
    E <error descr="Cannot assign 'Object' to 'E'">e17</error> = s
    E <error descr="Cannot assign 'Matcher' to 'E'">e18</error> = "aaa" =~ /aaa/
    E <error descr="Cannot assign 'List' to 'E'">e20</error> = [] as List
    E e26 = "F"
    E e27 = E.F
  }

  E enumReturn() {
    switch (null) {
      case 1: <error descr="Cannot return 'char' from method returning 'E'">return</error> 1 as char
      case 2: <error descr="Cannot return 'BigDecimal' from method returning 'E'">return</error> 1 as BigDecimal
      case 3: <error descr="Cannot return 'BigInteger' from method returning 'E'">return</error> 1 as BigInteger
      case 4: <error descr="Cannot return 'double' from method returning 'E'">return</error> 1 as double
      case 5: <error descr="Cannot return 'float' from method returning 'E'">return</error> 1 as float
      case 6: <error descr="Cannot return 'int' from method returning 'E'">return</error> 1 as int
      case 7: <error descr="Cannot return 'short' from method returning 'E'">return</error> 1 as short
      case 8: <error descr="Cannot return 'long' from method returning 'E'">return</error> 1 as long
      case 9: <error descr="Cannot return 'boolean' from method returning 'E'">return</error> 1 as boolean
      case 10: <warning descr="Cannot return 'void' from method returning 'E'">return</warning> explicitVoid()
      case 11: <error descr="Cannot return 'Date' from method returning 'E'">return</error> new Date()
      case 12: return <warning descr="Cannot find enum constant 'a' in enum 'E'">"a"</warning>
      case 13: return <weak_warning descr="Cannot assign string to enum 'E'">"${System.in.read()}"</weak_warning>
      case 14: <error descr="Cannot return 'Object' from method returning 'E'">return</error> new Object()
      case 15: <error descr="Cannot return 'Object[]' from method returning 'E'">return</error> new Object[0]
      case 16: <error descr="Cannot return 'Closure<Void>' from method returning 'E'">return</error> { int a, int t -> println(a + t) }
      case 17: <error descr="Cannot return 'Object' from method returning 'E'">return</error> s
      case 18: <error descr="Cannot return 'Matcher' from method returning 'E'">return</error> "aaa" =~ /aaa/
      case 20: <error descr="Cannot return 'List' from method returning 'E'">return</error> [] as List
      case 26: return <warning descr="Cannot find enum constant 'f' in enum 'E'">"f"</warning>
      case 27: return "F"
      case 28: return E.F
      default: return null
    }
  }

  def enumMethodCall() {
    fooEnum(null)
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(boolean)'">(1 as boolean)</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(char)'">(1 as char)</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(byte)'">(1 as byte)</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(double)'">(1 as double)</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(float)'">(1 as float)</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(int)'">(1 as int)</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(short)'">(1 as short)</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(long)'">(1 as long)</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(void)'">(explicitVoid())</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(java.util.Date)'">(new Date())</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(java.lang.String)'">('a')</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(groovy.lang.GString)'">("${System.in.read()}")</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '([])'">([])</error>
    fooEnum<error descr="'fooEnum' in 'FooEnum' cannot be applied to '(java.lang.String)'">("F")</error>
    fooEnum(E.F)
  }
}
