interface ApplicationFeature<in P : Pipeline<*>, B : Any, V>
open class Pipeline<TSubject : Any>()
fun <A : Pipeline<*>, T : Any, V> A.feature(<warning>feature</warning>: ApplicationFeature<error descr="[WRONG_NUMBER_OF_TYPE_ARGUMENTS]"><A, T></error>) : Unit {}
