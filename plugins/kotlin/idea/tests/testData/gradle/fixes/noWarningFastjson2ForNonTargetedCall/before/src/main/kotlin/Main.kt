// "Add 'kotlin-reflect.jar' to the classpath" "false"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.libraries.Fastjson2MissingKotlinReflectInspection
import com.alibaba.fastjson2.JSON

fun main() {
    JSON.parseObject(<caret>"""{"name":"Alice","age":30}""")
}
