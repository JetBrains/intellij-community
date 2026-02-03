package imports

import static com.foo.Bar.myProperty as abc

class PropertyWithAlias {
  def usage() {
    abc<warning descr="'abc' cannot be applied to '()'">()</warning>
    abc<warning descr="'abc' cannot be applied to '(java.lang.Integer)'">(1)</warning>
    abc<warning descr="'abc' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>

    abc
    abc = 1

    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>
    getAbc()
    getAbc<warning descr="'getMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer)'">(0)</warning>

    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>
    setAbc()
    setAbc(3)
    setAbc<warning descr="'setMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(4, 5)</warning>
  }
}

new PropertyWithAlias().usage()
