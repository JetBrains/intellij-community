import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import java.io.Serializable
import javax.swing.Icon

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

class ProjectConfigurable(@Suppress("UNUSED_PARAMETER") project: Project) : Configurable {
  override fun createComponent() = null
  override fun isModified() = false
  override fun getDisplayName() = "any"
  override fun apply() {
    // any
  }
}

class ProjectConfigurableProvider(@Suppress("unused") private val project: Project) : ConfigurableProvider() {
  override fun createConfigurable() = null
}

class MyFileType : LanguageFileType(Language.ANY) {
  override fun getDefaultExtension() = "any"
  override fun getDescription() = "any"
  override fun getIcon() = null
  override fun getName() = "any"
}

object ObjectFileTypeButReferencedByInstanceField : LanguageFileType(Language.ANY) {
  override fun getDefaultExtension() = "any"
  override fun getDescription() = "any"
  override fun getIcon() = null
  override fun getName() = "any"
}

class MyIntentionAction : IntentionAction {
  override fun startInWriteAction() = true
  override fun getText() = "any"
  override fun getFamilyName() = "any"
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    // any
  }
}

class MyApplicationService {
  fun any() = "any"
}

class MyProjectService {
  fun any() = "any"
}

class MyModuleService {
  fun any() = "any"
}

class ConfigurablesOuterClass {
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

  class ProjectConfigurable(@Suppress("UNUSED_PARAMETER") project: Project) : Configurable {
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
}

class FileTypesOuterClass {
  class MyFileType : LanguageFileType(Language.ANY) {
    override fun getDefaultExtension() = "any"
    override fun getDescription() = "any"
    override fun getIcon() = null
    override fun getName() = "any"
  }

  object ObjectFileTypeButReferencedByInstanceField : LanguageFileType(Language.ANY) {
    override fun getDefaultExtension() = "any"
    override fun getDescription() = "any"
    override fun getIcon() = null
    override fun getName() = "any"
  }
}

class IntentionActionsOuterClass {
  class MyIntentionAction : IntentionAction {
    override fun startInWriteAction() = true
    override fun getText() = "any"
    override fun getFamilyName() = "any"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
      // any
    }
  }
}

class ServicesOuterClass {
  class MyApplicationService {
    fun any() = "any"
  }

  class MyProjectService {
    fun any() = "any"
  }

  class MyModuleService {
    fun any() = "any"
  }
}

// correct objects that are not registered (to make sure no false positives are reported):
object MyObject {}

class OuterForCompanionObject {
  companion object {}
}
class OuterForNamedCompanionObject {
  companion object Named {}
}

fun any() {
  takeSerializable(object: Serializable {})
}

private fun takeSerializable(@Suppress("UNUSED_PARAMETER") serializable: Serializable) {
  // any
}
