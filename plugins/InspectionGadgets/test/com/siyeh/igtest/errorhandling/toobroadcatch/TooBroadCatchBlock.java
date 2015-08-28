package com.siyeh.igtest.errorhandling.toobroadcatch;

import java.io.FileNotFoundException;
import java.io.EOFException;
import java.io.IOException;
import java.net.URL;

public class TooBroadCatchBlock{
    public void foo(){
        try{
            if(bar()){
                throw new FileNotFoundException();
            } else{
                throw new EOFException();
            }
        } catch(FileNotFoundException e){
            e.printStackTrace();
        } catch(<warning descr="'catch' of 'IOException' is too broad, masking exception 'EOFException'">IOException</warning> e){
            e.printStackTrace();
        }
    }

    private boolean bar(){
        return false;
    }

    void foos() {
        try {
            new URL(null);
            throw new NullPointerException();
        } catch (<warning descr="'catch' of 'IOException' is too broad, masking exception 'MalformedURLException'">IOException</warning> e) {

        } catch (<warning descr="'catch' of 'RuntimeException' is too broad, masking exception 'NullPointerException'">RuntimeException</warning> e) {

        }
    }

  void bars(boolean a, boolean b, boolean c) {
    try {
      if (!a) {
        throw new NotActiveException(); // extends ObjectStreamException
      }
      if (b) {
        throw new StreamCorruptedException(); // extends ObjectStreamException
      }
      if (c) {
        throw new IOException();
      }
    } catch (<warning descr="'catch' of 'ObjectStreamException' is too broad, masking exceptions 'NotActiveException' and 'StreamCorruptedException'">ObjectStreamException</warning> e) {
      // Deal with ObjectStreamException (a subclass of IOException)...
    } catch (IOException e) {
      // Deal with IOException...
    }
  }

  class ObjectStreamException extends IOException {}
  class StreamCorruptedException extends ObjectStreamException {}
  class NotActiveException extends ObjectStreamException {}

  void test() {
    try {
      try (java.io.FileInputStream in = new java.io.FileInputStream("asdf")) {}
    } catch (<warning descr="'catch' of 'IOException' is too broad, masking exception 'FileNotFoundException'">IOException</warning> e) {}
    try (java.io.InputStream in = new java.io.FileInputStream("")) {

    } catch (<warning descr="'catch' of 'Exception' is too broad, masking exceptions 'IOException' and 'FileNotFoundException'">Exception</warning> e) {}
  }

  boolean m() {
    try {
      new java.io.FileInputStream("");
      return new java.io.File("can_reset").isFile();
    } catch (FileNotFoundException e) {
      return false;
    } catch (<warning descr="'catch' of 'Exception' is too broad, masking exception 'RuntimeException'">Exception</warning> e ) {
      return false;
    }
  }

  boolean m2() {
    try {
      new java.io.FileInputStream("");
      return new java.io.File("can_reset").isFile();
    } catch (<warning descr="'catch' of 'Exception' is too broad, masking exception 'FileNotFoundException'">Exception</warning> e ) {
      return false;
    }
  }

  void m3() {
    try {
      new Object() {
        void f() throws FileNotFoundException {
          throw new FileNotFoundException();
        }
      };
      throw new IOException();
    } catch (IOException e) {}
    try {
      System.out.println();
    } catch (<warning descr="'catch' of 'Exception' is too broad, masking exception 'RuntimeException'">Exception</warning> e) {

    }
    try {
      System.out.println();
    } catch (RuntimeException e) {

    } catch (Exception e) {

    }
    try {
      System.out.println();
      throw new NumberFormatException();
    } catch (<warning descr="'catch' of 'IllegalArgumentException' is too broad, masking exception 'NumberFormatException'">IllegalArgumentException</warning> | NullPointerException e) {

    }
  }

  void incomplete() {
    try {
      throw <error descr="Cannot resolve symbol 'undeclared'">undeclared</error>;
    } catch (NumberFormatException e) {}
  }
}
