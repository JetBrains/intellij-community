class A {
  boolean g(Class c) {return false;}

  boolean f() {
    if (g(this instanceof A <caret>? A.class : Object.class))
      return true;

    return false;
  }
}
