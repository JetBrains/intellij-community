class X {
  long typePromotion = <warning descr="'1L /*1*//*2*/ * Integer.MAX_VALUE */*3*//*4*/ Integer.MAX_VALUE' can be replaced with 'Integer.MAX_VALUE * Integer.MAX_VALUE'">1L<caret> /*1*//*2*/ * Integer.MAX_VALUE */*3*//*4*/ Integer.MAX_VALUE</warning>;
}