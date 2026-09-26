// IGNORE_K2

interface ApplicationFeature<in P : Pipeline<*>, B : Any, V>
open class Pipeline<TSubject : Any>()
fun <A : Pipeline<*>, T : Any, V> A.feature(feature: <error descr="[WRONG_NUMBER_OF_TYPE_ARGUMENTS]">ApplicationFeature<A, T></error>) : Unit {}
