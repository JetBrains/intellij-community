import kotlinx.serialization.Serializable


@Serializable
class CommonMain {

}

fun useCommonMain() {
    CommonMain.serializer()
}