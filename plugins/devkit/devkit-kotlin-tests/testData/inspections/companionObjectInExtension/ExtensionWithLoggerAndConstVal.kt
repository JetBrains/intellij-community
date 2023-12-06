import com.intellij.openapi.diagnostic.Logger

class LoggerSubclass : Logger()

typealias LoggerAlias = Logger

typealias LoggerChildAlias = LoggerSubclass

class MyExtensionImpl : MyExtension {

  companion object {
    // loggers are allowed
    private val LOGGER: Logger = Logger()

    // logger subclasses are allowed
    private val LOGGER_SUBCLASS = LoggerSubclass()

    // logger type via alias
    private val LOGGER_ALIAS: LoggerAlias = LOGGER

    // logger subclass via alias
    private val LOGGER_SUBCLASS_ALIAS: LoggerChildAlias = LOGGER_SUBCLASS
    
    // nullable loggers are allowed
    private val NULLABLE_LOGGER: Logger? = null

    // const val are allowed
    const val MY_CONST = 0
  }

}