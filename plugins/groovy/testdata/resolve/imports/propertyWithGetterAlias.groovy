package imports

import static com.foo.Bar.myProperty as getAbc

class PropertyWithGetterAlias {
  def usage() {
    <warning descr="Cannot resolve symbol 'abc'">abc</warning>         // https://issues.apache.org/jira/browse/GROOVY-8263
    getAbc<warning descr="'getAbc' cannot be applied to '()'">()</warning>
    getAbc<warning descr="'getAbc' cannot be applied to '(java.lang.Integer)'">(0)</warning>

    <warning descr="Cannot resolve symbol 'abc'">abc</warning> = 1
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>()
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>(3)
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>(4, 5)

    getAbc
    getGetAbc()
    getGetAbc<warning descr="'getMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer)'">(2)</warning>

    getAbc = 1
    setGetAbc()
    setGetAbc(3)
    setGetAbc<warning descr="'setMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(4, 5)</warning>

    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>
    <warning descr="Cannot resolve symbol 'getSetAbc'">getSetAbc</warning>()
    <warning descr="Cannot resolve symbol 'getSetAbc'">getSetAbc</warning>(0)

    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning> = 1
    <warning descr="Cannot resolve symbol 'setSetAbc'">setSetAbc</warning>()
    <warning descr="Cannot resolve symbol 'setSetAbc'">setSetAbc</warning>(1)
    <warning descr="Cannot resolve symbol 'setSetAbc'">setSetAbc</warning>(2, 3)

    <warning descr="Cannot resolve symbol 'getGetAbc'">getGetAbc</warning>
    <warning descr="Cannot resolve symbol 'setGetAbc'">setGetAbc</warning>
    <warning descr="Cannot resolve symbol 'getSetAbc'">getSetAbc</warning>
    <warning descr="Cannot resolve symbol 'setSetAbc'">setSetAbc</warning>
  }
}

new PropertyWithGetterAlias().usage()
