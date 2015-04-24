package com.siyeh.igtest.errorhandling.try_identical_catches;

class TryIdenticalCatches {
  public void notIdentical() {
    try {

    }
    catch(NumberFormatException e) {
      log(e);
    }
    catch(RuntimeException e) {
      throw e;
    }
  }

  String nonIdenticalButDuplicated(Object o) {
        try {
        } catch (NullPointerException e) {
            if (o instanceof String) {
                return a((String) o);
            }
        } catch (NumberFormatException e) {
            if (o instanceof String) {
                return b((String) o);
            }
        }
        return null;
  }

  String a(String s) { return s;}
  String b(String s) { return s;}


  public void nonIdenticalWithParameterValue() throws StorageException {
    try {
      throwAllExceptions();
    }
    catch (StorageInitializationException e) {
      throw e;
    }
    catch (java.io.IOException e) {
      throw new StorageInitializationException("Can not setup storage factory.", e);
    }
    catch (Exception e) {
      throw new StorageInitializationException("Unspecified exception occurs while DB storage initialization.", e);
    }
  }

  void throwAllExceptions() throws StorageInitializationException, java.io.IOException {
  }

  class StorageException extends Exception {
  }

  class StorageInitializationException extends StorageException {
    private StorageInitializationException(String m, Exception e) {
    }
  }

  public void identicalWithoutParams(boolean value) {
     try {
       if (value) {
         throw new ClassNotFoundException();
       }
       else {
         throw new NumberFormatException();
       }
     }
     catch(ClassNotFoundException cnfe) {
       System.out.println();
     }
     <warning descr="'catch' branch identical to 'ClassNotFoundException' branch">catch(NumberFormatException nfe)</warning> {
      System.out.println();
     }
   }

  public void identical(boolean value) {
    try {
      if (value) {
        throw new ClassNotFoundException();
      }
      else {
        throw new NumberFormatException();
      }
    }
    catch(ClassNotFoundException cnfe) {
      log(cnfe);
    }
    <warning descr="'catch' branch identical to 'ClassNotFoundException' branch">catch(NumberFormatException n<caret>fe)</warning> {
      log(nfe);
    }
  }

  private void log(Exception e) {
  }

  class E1 extends RuntimeException {}
  class E2 extends E1 {}
  class E3 extends RuntimeException {}
  class E4 extends E3 {}

  void p() {
    try {

    } catch (E4 e) {
    } <warning descr="'catch' branch identical to 'E4' branch">catch (E2 e)</warning> {
    } <warning descr="'catch' branch identical to 'E4' branch">catch (E3 e)</warning> {
    } <warning descr="'catch' branch identical to 'E2' branch">catch (E1 e)</warning> {
    }
  }

  void q() {
    try {
      Class.forName("bla").newInstance();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (<error descr="Cannot resolve symbol 'InstantiationException'">InstantiationException</error><error descr="Identifier expected"> </error>) {
      <error descr="Cannot resolve symbol 'e'">e</error>.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (IllegalAccessException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
  }

  public static void main() {
    Throwable causeException;
    try {
      throw new NullPointerException();
    } catch (final NullPointerException e) {
      causeException = e;
    } <warning descr="'catch' branch identical to 'NullPointerException' branch">catch (final IllegalArgumentException e)</warning> {
      causeException = e;
    } <warning descr="'catch' branch identical to 'NullPointerException' branch">catch (final IndexOutOfBoundsException e)</warning> {
      causeException = e;
    }
    System.out.println("causeException = " + causeException);
  }
}