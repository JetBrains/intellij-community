package com.foo.bar

import com.foo.baz.A
import groovy.transform.CompileStatic

@CompileStatic
class Usage {
  def foo() { A.foo { <error descr="Expected 'MyClass', found 'com.foo.bar.MyClass'">MyClass</error> a -> } }
}
