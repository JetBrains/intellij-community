package one.two

@Repeatable
@JvmRepeatable(JvmRepeatableAnnotationWithKotlinContainer::class)
annotation class JvmRepeatableAnnotationWithKotlin(val value: Int)
annotation class JvmRepeatableAnnotationWithKotlinContainer(val value: Array<JvmRepeatableAnnotationWithKotlin>)
