package imports

import static com.foo.Bar.setMyProperty as getAbc

class SetterWithGetterAlias {
  def usage() {
    <warning descr="Cannot resolve symbol 'abc'">abc</warning>()
    <warning descr="Cannot resolve symbol 'abc'">abc</warning>(1)
    <warning descr="Cannot resolve symbol 'abc'">abc</warning>(2, 3)

    <warning descr="Cannot resolve symbol 'abc'">abc</warning>       // https://issues.apache.org/jira/browse/GROOVY-8264
    <warning descr="Cannot resolve symbol 'abc'">abc</warning> = 1

    <warning descr="Cannot resolve symbol 'getAbc'">getAbc</warning>
    getAbc()
    getAbc(1)

    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>()
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>(3)
    <warning descr="Cannot resolve symbol 'setAbc'">setAbc</warning>(4, 5)
  }
}

new SetterWithGetterAlias().usage()
