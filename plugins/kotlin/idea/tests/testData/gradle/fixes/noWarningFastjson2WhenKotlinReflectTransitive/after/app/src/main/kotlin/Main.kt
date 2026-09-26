// "Add 'kotlin-reflect' to dependencies" "false"
import com.alibaba.fastjson2.JSON

data class User(val name: String, val age: Int)

fun main() {
    JSON.parseObject(<caret>"""{"name":"Alice","age":30}""", User::class.java)
}
