class X {
  int property

  int getProperty() {
    return property
  }

  void setProperty(int p) {
    property = p
  }
}

def a = new X()

a.<ref>property = 0