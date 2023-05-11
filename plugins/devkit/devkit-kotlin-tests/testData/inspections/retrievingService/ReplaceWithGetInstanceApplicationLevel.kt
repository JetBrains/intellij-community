import com.intellij.openapi.application.ApplicationManager

@Suppress("NO_REFLECTION_IN_CLASS_PATH")
fun main() {
  <weak_warning descr="Can be replaced with 'MyService.getInstance()' call">ApplicationManager.getApplication().getService<caret>(MyService::class.java)</weak_warning>
}