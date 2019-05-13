class C {
  public C(Feedback i) {
  }
}

class Feedback {
  String getOutputWindowName() {
    return null;
  }
}

class A {

  protected static class B extends C {

    public B() {
      super(new Feedback() {
              public void outputMessage() {
                getOutputWindowName();
              }
            }
      );
    }
  }
}