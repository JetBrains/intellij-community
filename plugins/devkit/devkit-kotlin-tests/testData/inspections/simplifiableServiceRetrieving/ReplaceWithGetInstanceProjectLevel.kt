import com.intellij.openapi.project.Project

@Suppress("NO_REFLECTION_IN_CLASS_PATH")
fun foo(project: Project) {
  project.<weak_warning descr="Can be replaced with 'MyService.getInstance()' call">get<caret>Service</weak_warning>(MyService::class.java)
}