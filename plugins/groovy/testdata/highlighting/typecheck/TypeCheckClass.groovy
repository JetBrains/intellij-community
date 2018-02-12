import groovy.transform.CompileStatic

@CompileStatic
class FooClass {

  void explicitVoid() {}

  def fooClass(Class c) {}
  def o = { a -> 1 }

  def classCast() {
    (Class) null
    <error descr="Cannot cast 'char' to 'Class'">(Class) 1 as char</error>
    <error descr="Cannot cast 'BigDecimal' to 'Class'">(Class) 1 as BigDecimal</error>
    <error descr="Cannot cast 'BigInteger' to 'Class'">(Class) 1 as BigInteger</error>
    <error descr="Cannot cast 'double' to 'Class'">(Class) 1 as double</error>
    <error descr="Cannot cast 'float' to 'Class'">(Class) 1 as float</error>
    <error descr="Cannot cast 'int' to 'Class'">(Class) 1 as int</error>
    <error descr="Cannot cast 'short' to 'Class'">(Class) 1 as short</error>
    <error descr="Cannot cast 'long' to 'Class'">(Class) 1 as long</error>
    <error descr="Cannot cast 'boolean' to 'Class'">(Class) 1 as boolean</error>
    <error descr="Cannot cast 'void' to 'Class'">(Class) 1 as void</error>
    <error descr="Cannot cast 'void' to 'Class'">(Class) explicitVoid()</error>
    <error descr="Cannot cast 'Date' to 'Class'">(Class) new Date()</error>
    <error descr="Cannot cast 'String' to 'Class'">(Class) "a"</error>
    <error descr="Cannot cast 'GString' to 'Class'">(Class) "${System.in.read()}"</error>
    (Class) new Object()
    <error descr="Cannot cast 'Object[]' to 'Class'">(Class) new Object[0]</error>
    <error descr="Cannot cast 'Closure<Integer>' to 'Class'">(Class) { int a, int b -> a + b }</error>
    (Class) o
    <error descr="Cannot cast 'Matcher' to 'Class'">(Class) "aaa" =~ /aaa/</error>
    <error descr="Cannot cast 'List' to 'Class'">(Class) []</error>
    <error descr="Cannot cast 'String' to 'Class'">(Class) "java.util.List"</error>
    (Class) List
  }

  def classAssignment() {
    Class c
    c = null
    <warning descr="Cannot assign 'char' to 'Class'">c</warning> = 1 as char
    <warning descr="Cannot assign 'BigDecimal' to 'Class'">c</warning> = 1 as BigDecimal
    <warning descr="Cannot assign 'BigInteger' to 'Class'">c</warning> = 1 as BigInteger
    <warning descr="Cannot assign 'double' to 'Class'">c</warning> = 1 as double
    <warning descr="Cannot assign 'float' to 'Class'">c</warning> = 1 as float
    <warning descr="Cannot assign 'int' to 'Class'">c</warning> = 1 as int
    <warning descr="Cannot assign 'short' to 'Class'">c</warning> = 1 as short
    <warning descr="Cannot assign 'long' to 'Class'">c</warning> = 1 as long
    <warning descr="Cannot assign 'boolean' to 'Class'">c</warning> = 1 as boolean
    <warning descr="Cannot assign 'void' to 'Class'">c</warning> = 1 as void
    <warning descr="Cannot assign 'void' to 'Class'">c</warning> = explicitVoid()
    <warning descr="Cannot assign 'Date' to 'Class'">c</warning> = new Date()
    <warning descr="Cannot assign 'String' to 'Class'">c</warning> = "a"
    <warning descr="Cannot assign 'GString' to 'Class'">c</warning> = "${System.in.read()}"
    <warning descr="Cannot assign 'Object' to 'Class'">c</warning> = new Object()
    <warning descr="Cannot assign 'Object[]' to 'Class'">c</warning> = new Object[0]
    <warning descr="Cannot assign 'Closure<Integer>' to 'Class'">c</warning> = { int a, int b -> a + b }
    <warning descr="Cannot assign 'Object' to 'Class'">c</warning> = o
    <warning descr="Cannot assign 'Matcher' to 'Class'">c</warning> = "aaa" =~ /aaa/
    <warning descr="Cannot assign 'List' to 'Class'">c</warning> = []
    c = "java.util.List"
    c = List
  }

  def classVariable() {
    Class c0 = null
    Class <warning descr="Cannot assign 'char' to 'Class'">c1</warning> = 1 as char
    Class <warning descr="Cannot assign 'BigDecimal' to 'Class'">c2</warning> = 1 as BigDecimal
    Class <warning descr="Cannot assign 'BigInteger' to 'Class'">c3</warning> = 1 as BigInteger
    Class <warning descr="Cannot assign 'double' to 'Class'">c4</warning> = 1 as double
    Class <warning descr="Cannot assign 'float' to 'Class'">c5</warning> = 1 as float
    Class <warning descr="Cannot assign 'int' to 'Class'">c6</warning> = 1 as int
    Class <warning descr="Cannot assign 'short' to 'Class'">c7</warning> = 1 as short
    Class <warning descr="Cannot assign 'long' to 'Class'">c8</warning> = 1 as long
    Class <warning descr="Cannot assign 'boolean' to 'Class'">c9</warning> = 1 as boolean
    Class <warning descr="Cannot assign 'void' to 'Class'">c10</warning> = 1 as void
    Class <warning descr="Cannot assign 'void' to 'Class'">c11</warning> = explicitVoid()
    Class <warning descr="Cannot assign 'Date' to 'Class'">c12</warning> = new Date()
    Class <warning descr="Cannot assign 'String' to 'Class'">c13</warning> = "a"
    Class <warning descr="Cannot assign 'GString' to 'Class'">c14</warning> = "${System.in.read()}"
    Class <warning descr="Cannot assign 'Object' to 'Class'">c15</warning> = new Object()
    Class <warning descr="Cannot assign 'Object[]' to 'Class'">c16</warning> = new Object[0]
    Class <warning descr="Cannot assign 'Closure<Integer>' to 'Class'">c17</warning> = { int a, int b -> a + b }
    Class <warning descr="Cannot assign 'Object' to 'Class'">c18</warning> = o
    Class <warning descr="Cannot assign 'Matcher' to 'Class'">c19</warning> = "aaa" =~ /aaa/
    Class <warning descr="Cannot assign 'List' to 'Class'">c20</warning> = []
    Class c21 = "java.util.List"
    Class c22 = List
  }

  Class classReturn() {
    switch (classAssignment()) {
      case 1: <warning descr="Cannot return 'char' from method returning 'Class'">return</warning> 1 as char
      case 2: <warning descr="Cannot return 'BigDecimal' from method returning 'Class'">return</warning> 1 as BigDecimal
      case 3: <warning descr="Cannot return 'BigInteger' from method returning 'Class'">return</warning> 1 as BigInteger
      case 4: <warning descr="Cannot return 'double' from method returning 'Class'">return</warning> 1 as double
      case 5: <warning descr="Cannot return 'float' from method returning 'Class'">return</warning> 1 as float
      case 6: <warning descr="Cannot return 'int' from method returning 'Class'">return</warning> 1 as int
      case 7: <warning descr="Cannot return 'short' from method returning 'Class'">return</warning> 1 as short
      case 8: <warning descr="Cannot return 'long' from method returning 'Class'">return</warning> 1 as long
      case 9: <warning descr="Cannot return 'boolean' from method returning 'Class'">return</warning> 1 as boolean
      case 10: <warning descr="Cannot return 'void' from method returning 'Class'">return</warning> 1 as void
      case 11: <warning descr="Cannot return 'void' from method returning 'Class'">return</warning> explicitVoid()
      case 12: <warning descr="Cannot return 'Date' from method returning 'Class'">return</warning> new Date()
      case 13: <warning descr="Cannot return 'String' from method returning 'Class'">return</warning> "a"
      case 14: <warning descr="Cannot return 'GString' from method returning 'Class'">return</warning> "${System.in.read()}"
      case 15: <warning descr="Cannot return 'Object' from method returning 'Class'">return</warning> new Object()
      case 16: <warning descr="Cannot return 'Object[]' from method returning 'Class'">return</warning> new Object[0]
      case 17: <warning descr="Cannot return 'Closure<Integer>' from method returning 'Class'">return</warning> { int a, int b -> a + b }
      case 18: <warning descr="Cannot return 'Object' from method returning 'Class'">return</warning> o
      case 19: <warning descr="Cannot return 'Matcher' from method returning 'Class'">return</warning> "aaa" =~ /aaa/
      case 20: <warning descr="Cannot return 'List' from method returning 'Class'">return</warning> []
      case 21: return "java.util.List"
      case 22: return List
      default: return null
    }
  }

  def classFunction() {
    fooClass(null)
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(char)'">(1 as char)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(java.math.BigDecimal)'">(1 as BigDecimal)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(java.math.BigInteger)'">(1 as BigInteger)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(double)'">(1 as double)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(float)'">(1 as float)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(int)'">(1 as int)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(short)'">(1 as short)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(long)'">(1 as long)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(boolean)'">(1 as boolean)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(void)'">(1 as void)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(void)'">(explicitVoid())</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(java.util.Date)'">(new Date())</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(java.lang.String)'">("a")</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(groovy.lang.GString)'">("${System.in.read()}")</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(java.lang.Object)'">(new Object())</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(java.lang.Object[])'">(new Object[0])</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(groovy.lang.Closure<java.lang.Integer>)'">({ int a, int b -> a + b })</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(java.lang.Object)'">(o)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(java.util.regex.Matcher)'">("aaa" =~ /aaa/)</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '([])'">([])</error>
    fooClass<error descr="'fooClass' in 'FooClass' cannot be applied to '(java.lang.String)'">("java.util.List")</error>
    fooClass(List)
  }
}