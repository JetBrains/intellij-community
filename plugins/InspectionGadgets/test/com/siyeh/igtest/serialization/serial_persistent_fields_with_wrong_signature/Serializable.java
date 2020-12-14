import java.io.*;

class C implements Serializable {
  private final ObjectStreamField[] <warning descr="'serialPersistentFields' field of a Serializable class is not declared 'private static final ObjectStreamField[]'">serialPersistentFields</warning> = null;
}

record R(int a) implements Serializable {
  static final ObjectStreamField[] <warning descr="'serialPersistentFields' field of a Serializable class is not declared 'private static final ObjectStreamField[]'">serialPersistentFields</warning> = null;
}

record Z(ObjectStreamField[] <warning descr="'serialPersistentFields' field of a Serializable class is not declared 'private static final ObjectStreamField[]'">serialPersistentFields</warning>) implements Serializable {
}