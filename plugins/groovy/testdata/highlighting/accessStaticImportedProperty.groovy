class Foo {
  static def foo = 2
  private static def bar = 3
  private static def baz = 4
  private static def getBaz() { baz }
  static def FOO = 2
  private static def BAR = 2
}

import static Foo.foo
import static Foo.bar
import static Foo.baz
import static Foo.FOO
import static Foo.BAR

print foo
print <warning descr="Access to 'bar' exceeds its access rights">bar</warning>
print <warning descr="Access to 'baz' exceeds its access rights">baz</warning>
print FOO
print <warning descr="Access to 'BAR' exceeds its access rights">BAR</warning>
