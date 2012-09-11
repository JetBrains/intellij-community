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

  void foo(Object o) {
    CastToConcreteClass c = (CastToConcreteClass)o;
    CastToConcreteClass c2 = CastToConcreteClass.class.cast(o);
    final Class<CastToConcreteClass> aClass = CastToConcreteClass.class;
    final CastToConcreteClass c3 = aClass.cast(o);
  }
}