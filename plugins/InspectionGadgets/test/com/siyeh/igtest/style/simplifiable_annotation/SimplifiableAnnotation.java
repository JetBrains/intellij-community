package com.siyeh.igtest.style.simplifiable_annotation;

public class SimplifiableAnnotation {

    <warning descr="Annotation '@ SuppressWarnings(value = &quot;blabla&quot;)' may be replaced with '@SuppressWarnings(value = &quot;blabla&quot;)'">@ SuppressWarnings(value = "blabla")</warning>
    <warning descr="Annotation '@ Deprecated()' may be replaced with '@Deprecated'">@ Deprecated()</warning>
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
<warning descr="Annotation '@ValueAnnotation({&quot;the value&quot;})' may be replaced with '@ValueAnnotation(&quot;the value&quot;)'">@ValueAnnotation({"the value"})</warning>
<warning descr="Annotation '@ArrayAnnotation(array = {&quot;first&quot;})' may be replaced with '@ArrayAnnotation(array=&quot;first&quot;)'">@ArrayAnnotation(array = {"first"})</warning>
class MyClass {
}