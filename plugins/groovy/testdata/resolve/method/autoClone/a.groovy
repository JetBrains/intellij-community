import groovy.transform.AutoClone

@AutoClone
class Foo {
  def foo
}

print new Foo().clo<ref>ne()
