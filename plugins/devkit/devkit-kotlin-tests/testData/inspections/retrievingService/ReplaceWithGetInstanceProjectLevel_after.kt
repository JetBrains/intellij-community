import com.intellij.openapi.project.Project

@Suppress("NO_REFLECTION_IN_CLASS_PATH")
fun foo(project: Project) {
    MyService.getInstance(project)
}