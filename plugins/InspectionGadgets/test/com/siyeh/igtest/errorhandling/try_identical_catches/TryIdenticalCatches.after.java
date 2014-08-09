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
     catch(NumberFormatException nfe) {
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
    catch(ClassNotFoundException | NumberFormatException cnfe) {
      log(cnfe);
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
    } catch (E2 e) {
    } catch (E3 e) {
    } catch (E1 e) {
    }
  }

  void q() {
    try {
      Class.forName("bla").newInstance();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (InstantiationException ) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
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
    } catch (final IllegalArgumentException e) {
      causeException = e;
    } catch (final IndexOutOfBoundsException e) {
      causeException = e;
    }
    System.out.println("causeException = " + causeException);
  }
}