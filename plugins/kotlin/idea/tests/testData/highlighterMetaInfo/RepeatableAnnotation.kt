// WITH_STDLIB
// MIN_JAVA_VERSION: 11
// ALLOW_ERRORS
// FILE: JaAnnedClass.java

@KtAnnRepeatable
@KtAnnRepeatable
public class JaAnnedClass {}

// FILE: KtAnn.kt
@Repeatable
annotation class KtAnnRepeatable
