class A {
  boolean g(Class c) {return false;}

  boolean f() {
      if (this instanceof A) {
          if (g(A.class))
              return true;
      } else {
          if (g(Object.class))
              return true;
      }

    return false;
  }
}
