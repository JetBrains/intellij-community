import kotlinx.serialization.Serializable

@Serializable
class NativeMain {

}

fun useNativeMain() {
    CommonMain.serializer()
    NativeMain.serializer()
}