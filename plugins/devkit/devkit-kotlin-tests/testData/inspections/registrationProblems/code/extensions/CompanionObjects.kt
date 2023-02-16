import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

class CompanionObjectConfigurable {
  companion <error descr="Kotlin object registered as extension">object</error> : Configurable {
    override fun createComponent() = null
    override fun isModified() = false
    override fun getDisplayName() = "any"
    override fun apply() {
      // any
    }
  }
}

class CompanionObjectConfigurableWithNamed {
  companion object <error descr="Kotlin object registered as extension">Named</error> : Configurable {
    override fun createComponent() = null
    override fun isModified() = false
    override fun getDisplayName() = "any"
    override fun apply() {
      // any
    }
  }
}

class CompanionObjectConfigurableProvider {
  companion <error descr="Kotlin object registered as extension">object</error> : ConfigurableProvider() {
    override fun createConfigurable() = null
  }
}

class CompanionObjectConfigurableProviderWithNamed {
  companion object <error descr="Kotlin object registered as extension">Named</error> : ConfigurableProvider() {
    override fun createConfigurable() = null
  }
}

class CompanionObjectFileType {
  companion <error descr="Kotlin object registered as extension">object</error> : LanguageFileType(Language.ANY) {
    override fun getDefaultExtension() = "any"
    override fun getDescription() = "any"
    override fun getIcon() = null
    override fun getName() = "any"
  }
}

class CompanionObjectIntentionAction {

  companion <error descr="Kotlin object registered as extension">object</error> : IntentionAction {
    override fun startInWriteAction() = true
    override fun getText() = "any"
    override fun getFamilyName() = "any"
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
      // any
    }
  }
}

class MyCompanionObjectApplicationService {
  companion <error descr="Kotlin object registered as extension">object</error> {
    fun any() = "any"
  }
}

class MyCompanionObjectProjectService {
  companion object <error descr="Kotlin object registered as extension">WithName</error> {
    fun any() = "any"
  }
}

class MyCompanionObjectModuleService {
  companion <error descr="Kotlin object registered as extension">object</error> {
    fun any() = "any"
  }
}
