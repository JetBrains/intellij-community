package one.two

@JvmRepeatable(JvmRepeatableAnnotationContainer::class)
annotation class JvmRepeatableAnnotation(val value: Int)
annotation class JvmRepeatableAnnotationContainer(val value: Array<JvmRepeatableAnnotation>)
