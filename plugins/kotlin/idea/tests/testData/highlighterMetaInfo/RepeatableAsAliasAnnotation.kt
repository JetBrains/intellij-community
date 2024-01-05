// WITH_STDLIB
// ALLOW_ERRORS
// MIN_JAVA_VERSION: 11
// FILE: JaAnnedClass.java

@KtAnnRepeatable
@KtAnnRepeatable
public class JaAnnedClass {}

// FILE: KtAnn.kt
import kotlin.annotation.Repeatable as KotlinRepeatable

@KotlinRepeatable
annotation class KtAnnRepeatable
