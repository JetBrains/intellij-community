import java.io.*;

public class <warning descr="Class 'SerializableDeserializableClassInSecureContext' may be serialized and deserialized, compromising security">SerializableDeserializableClassInSecureContext</warning> implements Serializable {
  private int sensitive = 736326;
}
class ClassWithoutState implements Serializable {
  static int i = 0;
  transient int j = 0;
}
class <warning descr="Class 'DeserializableClass' may be serialized, compromising security">DeserializableClass</warning> implements Serializable {
  private int sensitive = 736326;
  private void readObject(ObjectInputStream in) {
    throw new Error();
  }
}
class NonDeserializableClass implements Serializable {
  private int sensitive = 736326;
  private void readObject(ObjectInputStream in) {
    throw new Error();
  }
  public void writeObject(ObjectOutputStream out) {
    throw new Error();
  }
}
class <warning descr="Class 'SerializableClass' may be deserialized, compromising security">SerializableClass</warning> implements Serializable {
  private int sensitive = 736326;
  public void writeObject(ObjectOutputStream out) {
    throw new Error();
  }
}

interface Event extends Serializable {
  int sensitive = 736326;
}
class EventListener<E  extends Event> {
  private int sensitive = 736326;
}
class MyException extends Exception {}
enum E {
  A, B, C
}