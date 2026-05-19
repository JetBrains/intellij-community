package a

class Service {
    suspend fun alphaSuspend(): Unit = Unit
    fun betaNormal(): Unit = Unit
    suspend fun gammaSuspend(): Unit = Unit
    fun deltaNormal(): Unit = Unit
    fun epsilonNormal(): Unit = Unit
}
