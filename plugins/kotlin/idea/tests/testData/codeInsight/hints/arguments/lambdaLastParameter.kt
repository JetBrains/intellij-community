fun foo(index: Int, action: (String) -> String) {}

fun m() {
    foo(<hint text="index:"/>0) { "" }
}