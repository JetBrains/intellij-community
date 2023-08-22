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

object <error descr="Kotlin object registered as extension">SingletonConfigurable</error> : Configurable {
  override fun createComponent() = null
  override fun isModified() = false
  override fun getDisplayName() = "any"
  override fun apply() {
    // any
  }
}

object <error descr="Kotlin object registered as extension">SingletonConfigurableProvider</error> : ConfigurableProvider() {
  override fun createConfigurable() = null
}

object <error descr="Kotlin object registered as extension">SingletonFileType</error> : LanguageFileType(Language.ANY) {
  override fun getDefaultExtension() = "any"
  override fun getDescription() = "any"
  override fun getIcon() = null
  override fun getName() = "any"
}

object <error descr="Kotlin object registered as extension">SingletonIntentionAction</error> : IntentionAction {
  override fun startInWriteAction() = true
  override fun getText() = "any"
  override fun getFamilyName() = "any"
  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    // any
  }
}

object <error descr="Kotlin object registered as extension">MyApplicationSingletonService</error> {
  fun any() = "any"
}

object <error descr="Kotlin object registered as extension">MyProjectSingletonService</error> {
  fun any() = "any"
}

object <error descr="Kotlin object registered as extension">MyModuleSingletonService</error> {
  fun any() = "any"
}
