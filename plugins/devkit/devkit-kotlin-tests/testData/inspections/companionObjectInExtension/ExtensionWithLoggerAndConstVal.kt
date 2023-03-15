import com.intellij.openapi.diagnostic.Logger

class MyExtensionImpl : MyExtension {

  companion object {
    // loggers are allowed
    private val LOGGER: Logger = Logger()

    // const val are allowed
    const val MY_CONST = 0
  }

}