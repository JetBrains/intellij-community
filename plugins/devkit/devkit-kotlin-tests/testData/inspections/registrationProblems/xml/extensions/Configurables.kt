import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project

object SingletonConfigurable : Configurable {
  override fun createComponent() = null
  override fun isModified() = false
  override fun getDisplayName() = "any"
  override fun apply() {
    // any
  }
}

object SingletonConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable() = null
}


class ApplicationConfigurable : Configurable {
  override fun createComponent() = null
  override fun isModified() = false
  override fun getDisplayName() = "any"
  override fun apply() {
    // any
  }
}

class ApplicationConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable() = null
}

class ProjectConfigurable(project: Project) : Configurable {
  override fun createComponent() = null
  override fun isModified() = false
  override fun getDisplayName() = "any"
  override fun apply() {
    // any
  }
}

class ProjectConfigurableProvider(private val project: Project) : ConfigurableProvider() {
  override fun createConfigurable() = null
}
