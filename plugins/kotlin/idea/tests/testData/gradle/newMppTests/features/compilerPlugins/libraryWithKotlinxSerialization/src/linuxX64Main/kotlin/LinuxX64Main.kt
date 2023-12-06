import kotlinx.serialization.Serializable

@Serializable
class LinuxX64Main {

}

fun useLinuxX64Main() {
    CommonMain.serializer()
    NativeMain.serializer()
    LinuxX64Main.serializer()
}