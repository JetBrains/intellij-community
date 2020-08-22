import java.io.*;

class Test implements Serializable {

  private static final long serialVersionID = 7874493593505141603L;
  static final long serialVersionUID = 7874493593505141603L;

  private static final ObjectStreamField[] serialPersistentFiels = new ObjectStreamField[0];
  private static ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];

  private void writeObj(ObjectOutputStream out) throws IOException {
  }
  void writeObject(ObjectOutputStream out) throws IOException {
  }

  private void readObj(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }
  public void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
  }

  private void readObjNoData() throws ObjectStreamException {
  }
  private void readObjectNoData(Object o) throws ObjectStreamException {
  }
  protected void readObjectNoData() throws ObjectStreamException {
  }

  public Integer writeReplace() throws ObjectStreamException {
    return 1;
  }
  public Object writeReplace(int a) throws ObjectStreamException {
    return 1;
  }

  protected Object readResolve(int a) throws ObjectStreamException {
    return 1;
  }
  protected Integer readResolve() throws ObjectStreamException {
    return 1;
  }
}

class Foo {
  private static final long serialVersionUID = 7874493593505141603L;
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];
}

enum Bar {
  ;
  private static final long serialVersionUID = 7874493593505141603L;
  private static final ObjectStreamField[] serialPersistentFields = new ObjectStreamField[0];
}