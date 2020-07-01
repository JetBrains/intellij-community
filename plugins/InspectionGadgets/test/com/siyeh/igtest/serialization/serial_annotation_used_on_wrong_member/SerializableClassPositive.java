import java.io.*;

class Test implements Serializable {

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private static final long serialVersionID = 7874493593505141603L;
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  static final long serialVersionUID = 7874493593505141603L;

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private static final ObjectStreamField[] serialPersistentFiels = new ObjectStreamField[0];
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private static ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private void writeObj(ObjectOutputStream out) throws IOException {
  }
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  void writeObject(ObjectOutputStream out) throws IOException {
  }

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private void readObj(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  public void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private void readObjNoData() throws ObjectStreamException {
  }
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private void readObjectNoData(Object o) throws ObjectStreamException {
  }
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  protected void readObjectNoData() throws ObjectStreamException {
  }

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  public Integer writeReplace() throws ObjectStreamException {
    return 1;
  }
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  public Object writeReplace(int a) throws ObjectStreamException {
    return 1;
  }

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  protected Object readResolve(int a) throws ObjectStreamException {
    return 1;
  }
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  protected Integer readResolve() throws ObjectStreamException {
    return 1;
  }
}

class Foo {
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private static final long serialVersionUID = 7874493593505141603L;
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];
}
