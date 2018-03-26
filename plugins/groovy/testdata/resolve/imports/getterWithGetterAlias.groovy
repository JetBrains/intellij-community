package imports

import static com.foo.Bar.getMyProperty as getAbc

class GetterWithGetterAlias {
  def usage() {
    abc<warning descr="'abc' cannot be applied to '()'">()</warning>
    abc<warning descr="'abc' cannot be applied to '(java.lang.Integer)'">(1)</warning>
    abc<warning descr="'abc' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>

    abc
    <warning descr="Cannot resolve symbol 'abc'">abc</warning> = 1

    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>

    getAbc()
    getAbc<warning descr="'getMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer)'">(0)</warning>

    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>()
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>(3)
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>(4, 5)
  }
}

new GetterWithGetterAlias().usage()
