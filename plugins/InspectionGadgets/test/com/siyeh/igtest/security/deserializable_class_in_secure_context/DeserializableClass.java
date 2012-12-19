package com.siyeh.igtest.security.deserializable_class_in_secure_context;

import java.io.ObjectInputStream;
import java.io.Serializable;


public class DeserializableClass implements Serializable {
  private void readObject(ObjectInputStream in) {

  }
}
class NonDeserializableClass implements Serializable {
  private void readObject(ObjectInputStream in) {
    throw new Error();
  }
}
interface Event extends Serializable {

}
class EventListener<E  extends Event> {

}
class MyException extends Exception {}