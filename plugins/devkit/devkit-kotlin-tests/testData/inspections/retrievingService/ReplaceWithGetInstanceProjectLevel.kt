import com.intellij.openapi.project.Project

@Suppress("NO_REFLECTION_IN_CLASS_PATH")
fun foo(project: Project) {
  <weak_warning descr="Can be replaced with 'MyService.getInstance()' call">project.getService<caret>(MyService::class.java)</weak_warning>
}