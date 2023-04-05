import com.intellij.openapi.diagnostic.Logger

class ExtensionWithLoggerAndConstVal : MyExtension {

  companion object {
    // loggers are allowed
    private val LOGGER: Logger = Logger()

    // const val are allowed
    const val MY_CONST = 0
  }

}