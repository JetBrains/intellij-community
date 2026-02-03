package imports

import static com.foo.Bar.myProperty as setAbc

class PropertyWithSetterAlias {
  def usage() {
    <warning descr="Cannot resolve symbol 'abc'">abc</warning>
    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>()
    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>(0)

    <warning descr="Cannot resolve symbol 'abc'">abc</warning> = 1       // https://issues.apache.org/jira/browse/GROOVY-8263
    setAbc<warning descr="'setAbc' cannot be applied to '()'">()</warning>
    setAbc<warning descr="'setAbc' cannot be applied to '(java.lang.Integer)'">(3)</warning>
    setAbc<warning descr="'setAbc' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(4, 5)</warning>

    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>
    <warning descr="Cannot resolve symbol 'getGetAbc'">getGetAbc</warning>()
    <warning descr="Cannot resolve symbol 'getGetAbc'">getGetAbc</warning>(2)

    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning> = 1
    <warning descr="Cannot resolve symbol 'setGetAbc'">setGetAbc</warning>()
    <warning descr="Cannot resolve symbol 'setGetAbc'">setGetAbc</warning>(3)
    <warning descr="Cannot resolve symbol 'setGetAbc'">setGetAbc</warning>(4, 5)

    setAbc
    getSetAbc()
    getSetAbc<warning descr="'getMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer)'">(0)</warning>

    setAbc = 1
    setSetAbc()
    setSetAbc(1)
    setSetAbc<warning descr="'setMyProperty' in 'com.foo.Bar' cannot be applied to '(java.lang.Integer, java.lang.Integer)'">(2, 3)</warning>

    <warning descr="Cannot resolve symbol 'getGetAbc'">getGetAbc</warning>
    <warning descr="Cannot resolve symbol 'setGetAbc'">setGetAbc</warning>
    <warning descr="Cannot resolve symbol 'getSetAbc'">getSetAbc</warning>
    <warning descr="Cannot resolve symbol 'setSetAbc'">setSetAbc</warning>
  }
}

new PropertyWithSetterAlias().usage()
