class A {
  int property

  int getProperty() {
    return property
  }

  void setProperty(int p) {
    property = p
  }
}

def a = new A()

a.<ref>property = 0