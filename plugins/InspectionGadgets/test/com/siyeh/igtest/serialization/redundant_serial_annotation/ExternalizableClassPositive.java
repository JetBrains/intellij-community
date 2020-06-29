import java.io.*;

class Test implements Externalizable {

  <warning descr="@Serial annotation is redundant">@Serial</warning>
  private static final long serialVersionID = 7874493593505141603L;
  <warning descr="@Serial annotation is redundant">@Serial</warning>
  static final long serialVersionUID = 7874493593505141603L;

  <warning descr="@Serial annotation is redundant">@Serial</warning>
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  <warning descr="@Serial annotation is redundant">@Serial</warning>
  private void writeObject(ObjectOutputStream out) throws IOException {
  }

  <warning descr="@Serial annotation is redundant">@Serial</warning>
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  <warning descr="@Serial annotation is redundant">@Serial</warning>
  private void readObjectNoData() throws ObjectStreamException {
  }

  <warning descr="@Serial annotation is redundant">@Serial</warning>
  public Object writeReplace(int a) throws ObjectStreamException {
    return 1;
  }
  <warning descr="@Serial annotation is redundant">@Serial</warning>
  public Integer writeReplace() throws ObjectStreamException {
    return 1;
  }

  <warning descr="@Serial annotation is redundant">@Serial</warning>
  protected Object readResolve(int a) throws ObjectStreamException {
    return 1;
  }
  <warning descr="@Serial annotation is redundant">@Serial</warning>
  protected Integer readResolve() throws ObjectStreamException {
    return 1;
  }

  <warning descr="@Serial annotation is redundant">@Serial</warning>
  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
  }

  <warning descr="@Serial annotation is redundant">@Serial</warning>
  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }
}
