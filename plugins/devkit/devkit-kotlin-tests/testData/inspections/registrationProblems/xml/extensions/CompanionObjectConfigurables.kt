import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider

class CompanionObjectConfigurable {
  companion object : Configurable {
    override fun createComponent() = null
    override fun isModified() = false
    override fun getDisplayName() = "any"
    override fun apply() {
      // any
    }
  }
}

class CompanionObjectConfigurableWithNamed {
  companion object Named : Configurable {
    override fun createComponent() = null
    override fun isModified() = false
    override fun getDisplayName() = "any"
    override fun apply() {
      // any
    }
  }
}

class CompanionObjectConfigurableProvider {
  companion object : ConfigurableProvider() {
    override fun createConfigurable() = null
  }
}

class CompanionObjectConfigurableProviderWithNamed {
  companion object Named : ConfigurableProvider() {
    override fun createConfigurable() = null
  }
}
