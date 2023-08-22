import com.intellij.openapi.application.ApplicationManager

@Suppress("NO_REFLECTION_IN_CLASS_PATH")
fun main() {
  ApplicationManager.getApplication().<weak_warning descr="Can be replaced with 'MyService.getInstance()' call">get<caret>Service</weak_warning>(MyService::class.java)
}