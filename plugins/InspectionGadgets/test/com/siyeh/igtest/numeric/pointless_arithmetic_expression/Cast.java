class X {
  long typePromotion = <warning descr="'1L /*!*/ * Integer.MAX_VALUE * Integer.MAX_VALUE' can be replaced with 'Integer.MAX_VALUE * Integer.MAX_VALUE'">1L<caret> /*!*/ * Integer.MAX_VALUE * Integer.MAX_VALUE</warning>;
}