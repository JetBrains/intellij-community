// "Add 'kotlin-reflect.jar' to the classpath" "false"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.libraries.Fastjson2MissingKotlinReflectInspection
import com.alibaba.fastjson2.JSON

data class User(val name: String, val age: Int)

fun main() {
    JSON.parseObject(<caret>"""{"name":"Alice","age":30}""", User::class.java)
}
