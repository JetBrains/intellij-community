class BooleanMethodNameMustStartWithQuestion {

  public boolean areYouAllRight() {
    return true;
  }

  public boolean <warning descr="Boolean method name 'setup' does not start with question word">setup</warning>() {
    return false;
  }
}