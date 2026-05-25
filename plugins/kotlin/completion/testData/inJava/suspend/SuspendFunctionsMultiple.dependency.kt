package a

class Service {
    suspend fun firstSuspend(): Unit = Unit
    fun firstNormal(): Unit = Unit
    suspend fun secondSuspend(): Unit = Unit
    fun secondNormal(): Unit = Unit
}
