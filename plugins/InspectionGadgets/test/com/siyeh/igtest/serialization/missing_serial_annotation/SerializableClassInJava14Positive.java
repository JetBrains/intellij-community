import java.io.*;

class Test implements Serializable {

  private static final long <warning descr="'serialVersionUID' can be annotated with '@Serial' annotation">serialVersionUID</warning> = 7874493593505141603L;
  private static final ObjectStreamField[] <warning descr="'serialPersistentFields' can be annotated with '@Serial' annotation">serialPersistentFields</warning> = new ObjectStreamField[0];

  private void <warning descr="'writeObject()' can be annotated with '@Serial' annotation">writeObject</warning>(ObjectOutputStream out) throws IOException {
  }

  private void <warning descr="'readObject()' can be annotated with '@Serial' annotation">readObject</warning>(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  private void <warning descr="'readObjectNoData()' can be annotated with '@Serial' annotation">readObjectNoData</warning>() throws ObjectStreamException {
  }

  public Object <warning descr="'writeReplace()' can be annotated with '@Serial' annotation">writeReplace</warning>() throws ObjectStreamException {
    return 1;
  }

  protected Object <warning descr="'readResolve()' can be annotated with '@Serial' annotation">readResolve</warning>() throws ObjectStreamException {
    return 1;
  }
}

record R() implements Serializable {
  private static final long <warning descr="'serialVersionUID' can be annotated with '@Serial' annotation">serialVersionUID</warning> = 7874493593505141603L;
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  public Object <warning descr="'writeReplace()' can be annotated with '@Serial' annotation">writeReplace</warning>() throws ObjectStreamException {
    return 1;
  }

  protected Object <warning descr="'readResolve()' can be annotated with '@Serial' annotation">readResolve</warning>() throws ObjectStreamException {
    return 1;
  }

  private void writeObject(ObjectOutputStream out) throws IOException {
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  private void readObjectNoData() throws ObjectStreamException {
  }
}