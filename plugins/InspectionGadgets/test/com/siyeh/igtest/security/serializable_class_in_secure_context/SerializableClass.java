package com.siyeh.igtest.security.serializable_class_in_secure_context;

import java.io.ObjectOutputStream;
import java.io.Serializable;


public class SerializableClass implements Serializable {

  public void writeObject(ObjectOutputStream out) {

  }
}
class NonSerializableClass implements Serializable {
  public void writeObject(ObjectOutputStream out) {
    throw new Error();
  }
}
interface Event extends Serializable {

}
class EventListener<E  extends Event> {

}
class MyException extends Exception {}