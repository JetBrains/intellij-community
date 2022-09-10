// ALLOW_AST_ACCESS
interface KotlinWithCompanion {
    companion object {
        @JvmStatic
        val providers: String
            get() = ""
    }
}