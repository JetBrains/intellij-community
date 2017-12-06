import groovy.transform.CompileStatic

@CompileStatic
class FooString {

  void explicitVoid() {}

  def fooString(String b) {}
  def o = { -> 1 }

  def stringCast() {
    (String)null
    <error descr="Cannot cast 'char' to 'String'">(String)1 as char</error>
    <error descr="Cannot cast 'BigDecimal' to 'String'">(String)1 as BigDecimal</error>
    <error descr="Cannot cast 'BigInteger' to 'String'">(String)1 as BigInteger</error>
    <error descr="Cannot cast 'double' to 'String'">(String)1 as double</error>
    <error descr="Cannot cast 'float' to 'String'">(String)1 as float</error>
    <error descr="Cannot cast 'int' to 'String'">(String)1 as int</error>
    <error descr="Cannot cast 'short' to 'String'">(String)1 as short</error>
    <error descr="Cannot cast 'long' to 'String'">(String)1 as long</error>
    <error descr="Cannot cast 'boolean' to 'String'">(String)1 as boolean</error>
    <error descr="Cannot cast 'void' to 'String'">(String)1 as void</error>
    <error descr="Cannot cast 'void' to 'String'">(String)explicitVoid()</error>
    <error descr="Cannot cast 'Date' to 'String'">(String)new Date()</error>
    (String)"a"
    (String)"${System.in.read()}"
    (String)new Object()
    <error descr="Cannot cast 'Object[]' to 'String'">(String)new Object[0]</error>
    <error descr="Cannot cast 'Closure<Integer>' to 'String'">(String){ int a, int b -> a + b }</error>
    (String)o
    <error descr="Cannot cast 'Matcher' to 'String'">(String)"aaa" =~ /aaa/</error>
    <error descr="Cannot cast 'List' to 'String'">(String)[]</error>
  }

  def stringAssignment() {
    String s
    s = null
    s = 1 as char
    s = 1 as BigDecimal
    s = 1 as BigInteger
    s = 1 as double
    s = 1 as float
    s = 1 as int
    s = 1 as short
    s = 1 as long
    s = 1 as boolean
    <warning descr="Cannot assign 'void' to 'String'">s</warning> = explicitVoid()
    s = new Date()
    s = "a"
    s = "${System.in.read()}"
    s = new Object()
    s = new Object[0]
    s = { int a, int t -> println(a + t) }
    s = o
    s = "aaa" =~ /aaa/
    s = []
    s = [] as List
    s = <error descr="Constructor 'String' in 'java.lang.String' cannot be applied to '(java.lang.Integer, java.lang.Integer, java.lang.Integer)'">[1, 2, 3]</error>
    s = [1, 2, 3] as List
    s = ["true"]
    s = [1] as List
//        s = [a: 1, b: 2, c: 3] // error should be
    s = [a: 1, b: 2, c: 3] as Map
    s = [a: 1, b: 2, c: 3] as List
  }

  def stringVariable() {
    String s0 = null
    String s1 = 1 as char
    String s2 = 1 as BigDecimal
    String s3 = 1 as BigInteger
    String s4 = 1 as double
    String s5 = 1 as float
    String s6 = 1 as int
    String s7 = 1 as short
    String s8 = 1 as long
    String s9 = 1 as boolean
    String <warning descr="Cannot assign 'void' to 'String'">s10</warning> = explicitVoid()
    String s11 = new Date()
    String s12 = "a"
    String s13 = "${System.in.read()}"
    String s14 = new Object()
    String s15 = new Object[0]
    String s16 = { int a, int t -> println(a + t) }
    String s17 = o
    String s18 = "aaa" =~ /aaa/
    String s19 = []
    String s20 = [] as List
    String s21 = <error descr="Constructor 'String' in 'java.lang.String' cannot be applied to '(java.lang.Integer, java.lang.Integer, java.lang.Integer)'">[1, 2, 3]</error>
    String s22 = [1, 2, 3] as List
//        String s23 = [a: 1, b: 2, c: 3] // error should be
    String s24 = [a: 1, b: 2, c: 3] as Map
    String s25 = [a: 1, b: 2, c: 3] as List
  }

  String stringReturn() {
    switch (null) {
      case 1: return 1 as char
      case 2: return 1 as BigDecimal
      case 3: return 1 as BigInteger
      case 4: return 1 as double
      case 5: return 1 as float
      case 6: return 1 as int
      case 7: return 1 as short
      case 8: return 1 as long
      case 9: return 1 as boolean
      case 10: <warning descr="Cannot return 'void' from method returning 'String'">return</warning> explicitVoid()
      case 11: return new Date()
      case 12: return "a"
      case 13: return "${System.in.read()}"
      case 14: return new Object()
      case 15: return new Object[0]
      case 16: return { int a, int t -> println(a + t) }
      case 17: return o
      case 18: return "aaa" =~ /aaa/
      case 19: return []
      case 20: return [] as List
      case 21: return [1, 2, 3]
      case 22: return [1, 2, 3] as List
      case 23: return [a: 1, b: 2, c: 3]
      case 24: return [a: 1, b: 2, c: 3] as Map
      case 25: return [a: 1, b: 2, c: 3] as List
      default: return null
    }
  }

  def stringMethodCall() {
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(char)'">(1 as char)</error>
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(byte)'">(1 as byte)</error>
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(double)'">(1 as double)</error>
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(float)'">(1 as float)</error>
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(int)'">(1 as int)</error>
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(short)'">(1 as short)</error>
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(long)'">(1 as long)</error>
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(java.lang.Boolean)'">(true)</error>
    fooString(null)
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(void)'">(explicitVoid())</error>
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '(java.util.Date)'">(new Date())</error>
    fooString('a')
    fooString("${System.in.read()}")
    fooString<error descr="'fooString' in 'FooString' cannot be applied to '([])'">([])</error>
  }
}