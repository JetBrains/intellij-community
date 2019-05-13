package imports

import static com.foo.Bar.setMyProperty as setAbc

class SetterWithSetterAlias {
  def usage() {
    <warning descr="Cannot resolve symbol 'abc'">abc</warning>()
    <warning descr="Cannot resolve symbol 'abc'">abc</warning>(1)
    <warning descr="Cannot resolve symbol 'abc'">abc</warning>(2, 3)

    <warning descr="Cannot resolve symbol 'abc'">abc</warning>
    abc = 1

    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>
    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>()
    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>(0)

    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>
    setAbc()
    setAbc(3)
    setAbc<warning descr="'setMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(4, 5)</warning>
  }
}

new SetterWithSetterAlias().usage()
