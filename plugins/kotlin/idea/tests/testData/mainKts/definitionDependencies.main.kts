// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY

import kotlin.reflect.KClass

fun mainKtsDefaultImports(): List<KClass<out Annotation>> {
    return listOf(DependsOn::class, Repository::class, Import::class, CompilerOptions::class, ScriptFileLocation::class)
}

println(mainKtsDefaultImports())