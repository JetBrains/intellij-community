package dependency

interface ComponentInterface {
    operator fun component1(): Int
    fun component2(): Int

    val component3: Int
}