class Super implements Cloneable {
  @Override
  protected final Object clone() throws CloneNotSupportedException {
    return super.clone();
  }
}

class Sub extends Super {

}