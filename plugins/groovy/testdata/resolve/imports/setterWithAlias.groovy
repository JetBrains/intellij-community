package imports

import static com.foo.Bar.setMyProperty as abc

class SetterWithAlias {
  def usage() {
    abc()
    abc(1)
    abc<warning descr="'setMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>

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

new SetterWithAlias().usage()
