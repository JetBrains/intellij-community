class MyClass {
  private field
}

print MyClass.metaClass.getMethods()
print MyClass.<warning descr="Access to 'field' exceeds its access rights">field</warning>