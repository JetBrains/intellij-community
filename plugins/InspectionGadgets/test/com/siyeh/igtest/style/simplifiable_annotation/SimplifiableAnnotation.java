package com.siyeh.igtest.style.simplifiable_annotation;

public class SimplifiableAnnotation {

    <warning descr="Annotation '@ SuppressWarnings(value = \\"blabla\\")' can be simplified">@ SuppressWarnings(value = "blabla")</warning>
    <warning descr="Annotation '@ Deprecated()' can be simplified">@ Deprecated()</warning>
    Object foo() {
        return null;
    }
}
@interface ValueAnnotation {
  String[] value();
}
@interface ArrayAnnotation {
  String[] array();
}
<warning descr="Annotation '@ValueAnnotation({\\"the value\\"})' can be simplified">@ValueAnnotation({"the value"})</warning>
<warning descr="Annotation '@ArrayAnnotation(array = {\\"first\\"})' can be simplified">@ArrayAnnotation(array = {"first"})</warning>
class MyClass {

  @ <error descr="'value' missing though required">ValueAnnotation</error>
  int foo(@ArrayAnnotation(array="") String s) {
    return -1;
  }

  <warning descr="Annotation '@Two(i={1}, j = 2)' can be simplified">@Two(i={1}, j = 2)</warning>
  String s;
}
@interface Two {
  int[] i();
  int j();
}
