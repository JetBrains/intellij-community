package com.siyeh.igtest.abstraction.cast_to_concrete_class;

class CastToConcreteClass {

  private String field;

  @Override
  public boolean equals(Object obj) {
    try {
      CastToConcreteClass c = (CastToConcreteClass)obj;
      return c.field.equals(field);
    } catch (ClassCastException e) {
      return false;
    }
  }

  @Override
  public CastToConcreteClass clone() throws CloneNotSupportedException {
    return (CastToConcreteClass) super.clone();
  }

  void foo(Object o) {
    CastToConcreteClass c = (<warning descr="Cast to concrete class 'CastToConcreteClass'">CastToConcreteClass</warning>)o;
    CastToConcreteClass c2 = CastToConcreteClass.class.<warning descr="Cast to concrete class 'CastToConcreteClass'">cast</warning>(o);
    final Class<CastToConcreteClass> aClass = CastToConcreteClass.class;
    final CastToConcreteClass c3 = aClass.<warning descr="Cast to concrete class 'CastToConcreteClass'">cast</warning>(o);
  }
}