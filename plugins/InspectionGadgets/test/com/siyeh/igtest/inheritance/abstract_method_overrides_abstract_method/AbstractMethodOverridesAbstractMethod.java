package com.siyeh.igtest.inheritance.abstract_method_overrides_abstract_method;

import java.util.*;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractMethodOverridesAbstractMethod {
  public abstract Object foo() throws Exception;

  abstract void one(String s);

  abstract void two();

  public abstract void three();




}
abstract class Child extends AbstractMethodOverridesAbstractMethod
{
  public abstract String foo() ;

  abstract void one(@Nullable String s);

  /**
   * some documentation
   */
  abstract void two();

  public abstract void <warning descr="Abstract method 'three()' overrides abstract method">three</warning>();
}

class MethodTypeParams {
  interface Top
  {
    <T> List<T> getList();
  }

  interface Middle extends Top
  {
    <T> List<T> getList();
  }

  abstract class Bottom implements Middle
  {
    @Override
    public abstract <T> ArrayList<T> getList();
  }
}

class SuperclassSubst {
    interface Top<T>
    {
        T getList();
    }

    abstract class Bottom implements Top<String>
    {
        @Override
        public abstract String <warning descr="Abstract method 'getList()' overrides abstract method">getList</warning>();
    }
}

class Java8DefaultMethods {

  interface A {
    String method();
  }

  interface B {
    default String method() {
      return null;
    }
  }

  //without override would inherit abstract & default - red code
  interface C extends A, B {
    @Override
    String method();
  }

  interface Super
  {
      default void method()
      {
          System.out.println("super");
      }
  }
  
  interface Sub extends Super
  {
      @Override
      default void method()
      {
          System.out.println("sub");
      }
  }
}

class CovariantReturnTypes {
  interface A<T> {}
  interface Foo {
    A<? extends Number> test();
  }

  interface Bar extends Foo {
    @Override
    A<Integer> test();
  }
}

