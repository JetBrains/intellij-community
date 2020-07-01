import java.io.*;

class Test implements Externalizable {

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private static final long serialVersionID = 7874493593505141603L;
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  static final long serialVersionUID = 7874493593505141603L;

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private void writeObject(ObjectOutputStream out) throws IOException {
  }

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  private void readObjectNoData() throws ObjectStreamException {
  }

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  public Object writeReplace(int a) throws ObjectStreamException {
    return 1;
  }
  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  public Integer writeReplace() throws ObjectStreamException {
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

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
  }

  <warning descr="Annotated member is not the part of serialization mechanism">@Serial</warning>
  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }
}
