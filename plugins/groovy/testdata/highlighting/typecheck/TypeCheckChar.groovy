import groovy.transform.CompileStatic

@CompileStatic
class FooChar {

  void explicitVoid() {}
  def fooChar(char c) {}

  String s = 'hello world'
  String s1 = 's'  // one-char string
  GString gs = "${s + s + s}"
  def ds = "String defined with 'def'"
  def dgs = "GString defined with 'def' ${s + s + s}"

  def charCast() {
    <error descr="Cannot cast 'null' to 'char'">(char)null</error>
    (char) (1 as char)
    <error descr="Cannot cast 'BigDecimal' to 'char'">(char) (1 as BigDecimal)</error>
    <error descr="Cannot cast 'BigInteger' to 'char'">(char) (1 as BigInteger)</error>
    (char) (1 as double)
    (char) (1 as float)
    (char) (1 as int)
    (char) (1 as short)
    (char) (1 as long)
    <error descr="Cannot cast 'boolean' to 'char'">(char) (1 as boolean)</error>
    <error descr="Cannot cast 'void' to 'char'">(char) (1 as void)</error>
    <error descr="Cannot cast 'void' to 'char'">(char)explicitVoid()</error>
    <error descr="Cannot cast 'Date' to 'char'">(char)new Date()</error>
    (char)"a"
    <error descr="Cannot cast 'String' to 'char'">(char)"abcdef"</error>
    <error descr="Cannot cast 'GString' to 'char'">(char)"${'a'}"</error>
    <error descr="Cannot cast 'GString' to 'char'">(char)"${System.in.read()}"</error>
    (char)new Object()
    <error descr="Cannot cast 'Object[]' to 'char'">(char)new Object[0]</error>
    <error descr="Cannot cast 'Closure<Integer>' to 'char'">(char){ int a, int b -> a + b }</error>
    <error descr="Cannot cast 'Matcher' to 'char'">(char) ("aaa" =~ /aaa/)</error>
    <error descr="Cannot cast 'List' to 'char'">(char)[]</error>
    <error descr="Cannot cast 'String' to 'char'">(char)s</error>
    <error descr="Cannot cast 'String' to 'char'">(char)s1</error>
    <error descr="Cannot cast 'GString' to 'char'">(char)gs</error>
    (char)ds
    (char)dgs
  }

  def charAssignment() {
    char c
    <error descr="Cannot assign 'null' to 'char'">c</error> = null
    c = 1 as char
    <error descr="Cannot assign 'BigDecimal' to 'char'">c</error> = 1 as BigDecimal
    <error descr="Cannot assign 'BigInteger' to 'char'">c</error> = 1 as BigInteger
    c = 1 as double
    c = 1 as float
    c = 1 as int
    c = 1 as short
    c = 1 as long
    <warning descr="Cannot assign 'boolean' to 'char'">c</warning> = 1 as boolean
    <error descr="Cannot assign 'void' to 'char'">c</error> = 1 as void
    <error descr="Cannot assign 'void' to 'char'">c</error> = explicitVoid()
    <error descr="Cannot assign 'Date' to 'char'">c</error> = new Date()
    c = "a"
    <error descr="Cannot assign 'String' to 'char'">c</error> = "abcdef"
    <error descr="Cannot assign 'GString' to 'char'">c</error> = "${'a'}"
    <error descr="Cannot assign 'GString' to 'char'">c</error> = "${System.in.read()}"
    <error descr="Cannot assign 'Object' to 'char'">c</error> = new Object()
    <error descr="Cannot assign 'Object[]' to 'char'">c</error> = new Object[0]
    <error descr="Cannot assign 'Closure<Integer>' to 'char'">c</error> = { int a, int b -> a + b }
    <error descr="Cannot assign 'Matcher' to 'char'">c</error> = "aaa" =~ /aaa/
    <warning descr="Cannot assign 'List' to 'char'">c</warning> = []
    <error descr="Cannot assign 'String' to 'char'">c</error> = s
    <error descr="Cannot assign 'String' to 'char'">c</error> = s1
    <error descr="Cannot assign 'GString' to 'char'">c</error> = gs
    <error descr="Cannot assign 'Object' to 'char'">c</error> = ds
    <error descr="Cannot assign 'Object' to 'char'">c</error> = dgs
  }

  def charVariable() {
    char <error descr="Cannot assign 'null' to 'char'">c0</error> = null
    char c1 = 1 as char
    char <error descr="Cannot assign 'BigDecimal' to 'char'">c2</error> = 1 as BigDecimal
    char <error descr="Cannot assign 'BigInteger' to 'char'">c3</error> = 1 as BigInteger
    char c4 = 1 as double
    char c5 = 1 as float
    char c6 = 1 as int
    char c7 = 1 as short
    char c8 = 1 as long
    char <warning descr="Cannot assign 'boolean' to 'char'">c9</warning> = 1 as boolean
    char <error descr="Cannot assign 'void' to 'char'">c10</error> = 1 as void
    char <error descr="Cannot assign 'void' to 'char'">c11</error> = explicitVoid()
    char <error descr="Cannot assign 'Date' to 'char'">c12</error> = new Date()
    char c13 = "a"
    char <error descr="Cannot assign 'String' to 'char'">c14</error> = "abcdef"
    char <error descr="Cannot assign 'GString' to 'char'">c15</error> = "${'a'}"
    char <error descr="Cannot assign 'GString' to 'char'">c16</error> = "${System.in.read()}"
    char <error descr="Cannot assign 'Object' to 'char'">c17</error> = new Object()
    char <error descr="Cannot assign 'Object[]' to 'char'">c18</error> = new Object[0]
    char <error descr="Cannot assign 'Closure<Integer>' to 'char'">c19</error> = { int a, int b -> a + b }
    char <error descr="Cannot assign 'Matcher' to 'char'">c20</error> = "aaa" =~ /aaa/
    char <warning descr="Cannot assign 'List' to 'char'">c21</warning> = []
    char <error descr="Cannot assign 'String' to 'char'">c22</error> = s
    char <error descr="Cannot assign 'String' to 'char'">c23</error> = s1
    char <error descr="Cannot assign 'GString' to 'char'">c24</error> = gs
    char <error descr="Cannot assign 'Object' to 'char'">c25</error> = ds
    char <error descr="Cannot assign 'Object' to 'char'">c26</error> = dgs
  }

  char charReturn() {
    switch (1) {
      case 0: <warning descr="Cannot return 'null' from method returning 'char'">return</warning> null
      case 1: return 1 as char
      case 2: <error descr="Cannot return 'BigDecimal' from method returning 'char'">return</error> 1 as BigDecimal
      case 3: <error descr="Cannot return 'BigInteger' from method returning 'char'">return</error> 1 as BigInteger
      case 4: return 1 as double
      case 5: return 1 as float
      case 6: return 1 as int
      case 7: return 1 as short
      case 8: return 1 as long
      case 9: <warning descr="Cannot return 'boolean' from method returning 'char'">return</warning> 1 as boolean
      case 10: <warning descr="Cannot return 'void' from method returning 'char'">return</warning> 1 as void
      case 11: <warning descr="Cannot return 'void' from method returning 'char'">return</warning> explicitVoid()
      case 12: <error descr="Cannot return 'Date' from method returning 'char'">return</error> new Date()
      case 13: <error descr="Cannot return 'String' from method returning 'char'">return</error> "a"
      case 14: <error descr="Cannot return 'String' from method returning 'char'">return</error> "abcdef"
      case 15: <error descr="Cannot return 'GString' from method returning 'char'">return</error> "${'a'}"
      case 16: <error descr="Cannot return 'GString' from method returning 'char'">return</error> "${System.in.read()}"
      case 17: <error descr="Cannot return 'Object' from method returning 'char'">return</error> new Object()
      case 18: <error descr="Cannot return 'Object[]' from method returning 'char'">return</error> new Object[0]
      case 19: <error descr="Cannot return 'Closure<Integer>' from method returning 'char'">return</error> { int a, int b -> a + b }
      case 20: <error descr="Cannot return 'Matcher' from method returning 'char'">return</error> "aaa" =~ /aaa/
      case 21: <error descr="Cannot return 'List' from method returning 'char'">return</error> []
      case 22: <error descr="Cannot return 'String' from method returning 'char'">return</error> s
      case 23: <error descr="Cannot return 'String' from method returning 'char'">return</error> s1
      case 24: <error descr="Cannot return 'GString' from method returning 'char'">return</error> gs
      case 25: <error descr="Cannot return 'Object' from method returning 'char'">return</error> ds
      case 26: <error descr="Cannot return 'Object' from method returning 'char'">return</error> dgs
      default: return 1
    }
  }

  def charMethodCalls() {
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(null)'">(null)</error>
    fooChar(1 as char)
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.math.BigDecimal)'">(1 as BigDecimal)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.math.BigInteger)'">(1 as BigInteger)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(double)'">(1 as double)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(float)'">(1 as float)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(int)'">(1 as int)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(short)'">(1 as short)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(long)'">(1 as long)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(boolean)'">(1 as boolean)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(void)'">(1 as void)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(void)'">(explicitVoid())</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.util.Date)'">(new Date())</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.lang.String)'">("a")</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.lang.String)'">("abcdef")</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(groovy.lang.GString)'">("${'a'}")</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(groovy.lang.GString)'">("${System.in.read()}")</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.lang.Object)'">(new Object())</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.lang.Object[])'">(new Object[0])</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(groovy.lang.Closure<java.lang.Integer>)'">({ int a, int b -> a + b })</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.util.regex.Matcher)'">("aaa" =~ /aaa/)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '([])'">([])</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.lang.String)'">(s)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.lang.String)'">(s1)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(groovy.lang.GString)'">(gs)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.lang.Object)'">(ds)</error>
    fooChar<error descr="'fooChar' in 'FooChar' cannot be applied to '(java.lang.Object)'">(dgs)</error>
  }
}

