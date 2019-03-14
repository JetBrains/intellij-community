import groovy.transform.CompileStatic

@CompileStatic
class FooBool {

  void explicitVoid() {}
  def fooBoolean(boolean b) {}
  def s = ""

  def boolCast() {
    (boolean) null
    <error descr="Cannot cast 'char' to 'boolean'">(boolean) (1 as char)</error>
    <error descr="Cannot cast 'BigDecimal' to 'boolean'">(boolean) (1 as BigDecimal)</error>
    <error descr="Cannot cast 'BigInteger' to 'boolean'">(boolean) (1 as BigInteger)</error>
    <error descr="Cannot cast 'double' to 'boolean'">(boolean) (1 as double)</error>
    <error descr="Cannot cast 'float' to 'boolean'">(boolean) (1 as float)</error>
    <error descr="Cannot cast 'int' to 'boolean'">(boolean) (1 as int)</error>
    <error descr="Cannot cast 'short' to 'boolean'">(boolean) (1 as short)</error>
    <error descr="Cannot cast 'long' to 'boolean'">(boolean) (1 as long)</error>
    (boolean) (1 as boolean)
    <error descr="Cannot cast 'void' to 'boolean'">(boolean) (1 as void)</error>
    <error descr="Cannot cast 'void' to 'boolean'">(boolean) explicitVoid()</error>
    <error descr="Cannot cast 'Date' to 'boolean'">(boolean) new Date()</error>
    <error descr="Cannot cast 'String' to 'boolean'">(boolean) "a"</error>
    <error descr="Cannot cast 'GString' to 'boolean'">(boolean) "${System.in.read()}"</error>
    (boolean) new Object()
    <error descr="Cannot cast 'Object[]' to 'boolean'">(boolean) new Object[0]</error>
    <error descr="Cannot cast 'Closure<Integer>' to 'boolean'">(boolean) { int a, int b -> a + b }</error>
    (boolean) s
    <error descr="Cannot cast 'Matcher' to 'boolean'">(boolean) ("aaa" =~ /aaa/)</error>
    <error descr="Cannot cast 'List' to 'boolean'">(boolean) []</error>
  }

  def boolAssignment() {
    boolean b
    b = null
    b = 1 as char
    b = 1 as BigDecimal
    b = 1 as BigInteger
    b = 1 as double
    b = 1 as float
    b = 1 as int
    b = 1 as short
    b = 1 as long
    b = 1 as boolean
    b = explicitVoid()
    b = new Date()
    b = "a"
    b = "${System.in.read()}"
    b = new Object()
    b = new Object[0]
    b = { int a, int t -> println(a + t) }
    b = s
    b = "aaa" =~ /aaa/
    b = []
    b = [] as List
    b = <error descr="Constructor 'Boolean' in 'java.lang.Boolean' cannot be applied to '(java.lang.Integer, java.lang.Integer, java.lang.Integer)'">[1, 2, 3]</error>
    b = [1, 2, 3] as List
    b = ["true"]
    b = [true]
    b = [1] as List
    b = <error descr="Constructor 'Boolean' in 'java.lang.Boolean' cannot be applied to '(['a':java.lang.Integer, 'b':java.lang.Integer,...])'">[a: 1, b: 2, c: 3]</error>
    b = [a: 1, b: 2, c: 3] as Map
    b = [a: 1, b: 2, c: 3] as List
  }

  def boolVariable() {
    boolean b0 = null
    boolean b1 = 1 as char
    boolean b2 = 1 as BigDecimal
    boolean b3 = 1 as BigInteger
    boolean b4 = 1 as double
    boolean b5 = 1 as float
    boolean b6 = 1 as int
    boolean b7 = 1 as short
    boolean b8 = 1 as long
    boolean b9 = 1 as boolean
    boolean b10 = explicitVoid()
    boolean b11 = new Date()
    boolean b12 = "a"
    boolean b13 = "${System.in.read()}"
    boolean b14 = new Object()
    boolean b15 = new Object[0]
    boolean b16 = { int a, int t -> println(a + t) }
    boolean b17 = s
    boolean b18 = "aaa" =~ /aaa/
    boolean b19 = []
    boolean b20 = [] as List
    boolean b21 = <error descr="Constructor 'Boolean' in 'java.lang.Boolean' cannot be applied to '(java.lang.Integer, java.lang.Integer, java.lang.Integer)'">[1, 2, 3]</error>
    boolean b22 = [1, 2, 3] as List
    boolean b23 = <error descr="Constructor 'Boolean' in 'java.lang.Boolean' cannot be applied to '(['a':java.lang.Integer, 'b':java.lang.Integer,...])'">[a: 1, b: 2, c: 3]</error>
    boolean b24 = [a: 1, b: 2, c: 3] as Map
    boolean b25 = [a: 1, b: 2, c: 3] as List
  }

  boolean boolReturn() {
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
      case 10: return explicitVoid()
      case 11: return new Date()
      case 12: return "a"
      case 13: return "${System.in.read()}"
      case 14: return new Object()
      case 15: return new Object[0]
      case 16: return { int a, int t -> println(a + t) }
      case 17: return s
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

  def booleanMethodCalls() {
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(char)'">(1 as char)</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(byte)'">(1 as byte)</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(double)'">(1 as double)</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(float)'">(1 as float)</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(int)'">(1 as int)</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(short)'">(1 as short)</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(long)'">(1 as long)</error>
    fooBoolean(true)
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(null)'">(null)</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(void)'">(explicitVoid())</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(java.util.Date)'">(new Date())</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(java.lang.String)'">('a')</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '(groovy.lang.GString)'">("${System.in.read()}")</error>
    fooBoolean<error descr="'fooBoolean' in 'FooBool' cannot be applied to '([])'">([])</error>
  }
}