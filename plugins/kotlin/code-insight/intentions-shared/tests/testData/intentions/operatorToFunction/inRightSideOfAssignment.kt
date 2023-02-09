// AFTER-WARNING: The value 'map.get("")' assigned to 'val a: String? defined in foo' is never used
// AFTER-WARNING: Variable 'a' is assigned but never accessed
fun foo(map: Map<String, String>) {
    val a: String?
    a = map<caret>[""]
}