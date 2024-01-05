// WITH_STDLIB
// ALLOW_ERRORS
// MIN_JAVA_VERSION: 11
// FILE: JaAnnedClass.java

@<!HIGHLIGHTING("severity='ERROR'; descr='Duplicate annotation. The declaration of 'KtAnnRepeatable' does not have a valid java.lang.annotation.Repeatable annotation'")!>KtAnnRepeatable<!>
@<!HIGHLIGHTING("severity='ERROR'; descr='Duplicate annotation. The declaration of 'KtAnnRepeatable' does not have a valid java.lang.annotation.Repeatable annotation'")!>KtAnnRepeatable<!>
public class JaAnnedClass {}
