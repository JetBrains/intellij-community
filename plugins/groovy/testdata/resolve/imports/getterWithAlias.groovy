package imports

import static com.foo.Bar.getMyProperty as abc

class GetterWithAlias {
  def usage() {
    abc()
    abc<warning descr="'getMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer)'">(1)</warning>
    abc<warning descr="'getMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>

    <warning descr="Cannot resolve symbol 'abc'">abc</warning>
    <warning descr="Cannot resolve symbol 'abc'">abc</warning> = 1

    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>
    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>()
    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>(0)

    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>()
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>(3)
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>(4, 5)
  }
}

new GetterWithAlias().usage()
