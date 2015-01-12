public class CloneInNonCloneableClass {

  public final Object clone() throws CloneNotSupportedException {
    // don't warn on final method that only throws CloneNotSupportedException
    throw new CloneNotSupportedException();
  }
}
class AB {
  @Override
  public Object <warning descr="'clone()' defined in non-Cloneable class 'AB'">clone</warning>() throws CloneNotSupportedException {
    return super.clone();
  }
}