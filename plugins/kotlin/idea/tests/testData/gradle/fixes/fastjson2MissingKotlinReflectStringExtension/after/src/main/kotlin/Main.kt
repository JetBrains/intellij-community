// "Add 'kotlin-reflect.jar' to the classpath" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.libraries.Fastjson2MissingKotlinReflectInspection
import com.alibaba.fastjson2.parseObject

data class User(val name: String, val age: Int)

fun main() {
    """{"name":"Alice","age":30}""".parseObject<User>()
}
