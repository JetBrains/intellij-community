package sample

enum class Abi {
    X86_ABI,
    ARM64_V8A_ABI,
}

class DeviceInfo(
    val anonymizedSerialNumber: String,
    val buildTags: String,
    val cpuAbi: Abi,
    val characteristicsList: List<String>,
)

object AnonymizerUtil {
    fun anonymizeUtf8(value: String): String = value
}
