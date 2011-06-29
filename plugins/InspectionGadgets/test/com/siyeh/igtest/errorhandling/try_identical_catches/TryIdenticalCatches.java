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
    <warning descr="Identical 'catch' branches in 'try' statement">catch(NumberFormatException n<caret>fe)</warning> {
      log(nfe);
    }
  }

  private void log(Exception e) {
  }
}