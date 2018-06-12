package com.siyeh.igtest.bugs.equals_which_doesnt_check_parameter;

import java.util.Optional;

public class EqualsWhichDoesntCheckParameterClass {
    private int foo;

    public boolean <warning descr="'equals()' should check the class of its parameter">equals</warning>(Object o) {
        if (this == o) return true;
        //if (o instanceof EqualsWhichDoesntCheckParameterClassInspection) return false;
        //if (getClass() != o.getClass()) return false;

        final EqualsWhichDoesntCheckParameterClass equalsWhichDoesntCheckParameterClass = (EqualsWhichDoesntCheckParameterClass) o;

        if (foo != equalsWhichDoesntCheckParameterClass.foo) return false;

        return true;
    }

    public int hashCode() {
        return foo;
    }
}
class One {
    private int one;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof One)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return one;
    }
}
class Two {
    private int two;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return two;
    }
}
class Three {
    private int three;

    @Override
    public boolean equals(Object o) {
        if (!getClass().isInstance(o)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return three;
    }
}
class Four {
    @Override
    public boolean equals(Object obj) {
        return false;
    }
}
class Five {
    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
class Parent {
  private String parentField;

  public boolean equals(Object o) {
    if (! getClass().isAssignableFrom(o.getClass())) {return false;}
    return ((Parent)o).parentField.equals(parentField);
  }
}

class Child {
  private String childField;

  public boolean equals(Object o) {
    if (! super.equals(o)) {return false;}
    return ((Child)o).childField.equals(childField);
  }
}
class Six {
  @Override
  public boolean equals(Object obj) {
    return this == obj;
  }
}
class Cell {
  int x, y;

  @Override
  public boolean equals(Object obj) {
    return Optional.ofNullable(obj)
                   .filter(Cell.class::isInstance)
                   .map(that -> (Cell)that)
                   .filter(that -> x == that.x && y == that.y)
                   .isPresent();
  }
}
class Cell2 {
  int x, y;

  @Override
  public boolean equals(Object obj) {
    return Optional.ofNullable(obj)
                   .filter(x -> x instanceof Cell2)
                   .map(that -> (Cell2)that)
                   .filter(that -> x == that.x && y == that.y)
                   .isPresent();
  }
}