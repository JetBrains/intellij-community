import java.io.*;

class <warning descr="Externalizable class 'C' defines 'readObject()'">C</warning> implements Externalizable {

  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }

  private void readObject(java.io.ObjectInputStream str) {
  }
}

class <warning descr="Externalizable class 'D' defines 'readObject()' and 'writeObject()'">D</warning> implements Externalizable {

  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }

  private void readObject(java.io.ObjectInputStream str) {
  }

  private void writeObject(java.io.ObjectOutputStream str) {
    new <warning descr="Externalizable anonymous class extending 'D' defines 'readObject()'">D</warning>(){
      private void readObject(java.io.ObjectInputStream str){
      }
    };
  }
}

record <warning descr="Externalizable record 'R' defines 'writeObject()'">R</warning>() implements Externalizable {

  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }

  private void writeObject(java.io.ObjectOutputStream str) {
  }
}

record Z() implements Externalizable {

  public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
  }

  public void writeExternal(ObjectOutput out) throws IOException {
  }

  private void writeObject() {
  }
}
interface <warning descr="Externalizable interface 'I' defines 'readObject()'">I</warning> extends Externalizable {

  void readObject(java.io.ObjectInputStream str);
}

