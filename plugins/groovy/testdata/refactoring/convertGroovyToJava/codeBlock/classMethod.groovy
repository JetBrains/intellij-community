class A {
  static int foo() {1}
  static getCanonicalName() { "" }
}

print A.foo()
print A.class.foo()
print A.simpleName
print A.class.simpleName
print A.canonicalName
print A.class.canonicalName
