class AnonymousClass {

  {
    B settings = new B() {
      public B clone() throws CloneNotSupportedException {
          return (B) super.clone();
      }

    };
  }

}

class B implements Cloneable {

}