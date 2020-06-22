import java.io.*;

class Test implements Externalizable {

  private static final long serialVersionID = 7874493593505141603L;
  static final long serialVersionUID = 7874493593505141603L;

  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  private void writeObject(ObjectOutputStream out) throws IOException {
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  private void readObjectNoData() throws ObjectStreamException {
  }

  public Object writeReplace(int a) throws ObjectStreamException {
    return 1;
  }
  public Integer writeReplace() throws ObjectStreamException {
    return 1;
  }

  protected Object readResolve(int a) throws ObjectStreamException {
    return 1;
  }
  protected Integer readResolve() throws ObjectStreamException {
    return 1;
  }

  @Override
  public void writeExternal(ObjectOutput out) throws IOException {
  }

  @Override
  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }
}