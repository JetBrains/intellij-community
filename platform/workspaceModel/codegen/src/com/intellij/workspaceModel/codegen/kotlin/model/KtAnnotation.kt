package storage.codegen.patcher

class KtAnnotation(val name: SrcRange, val args: List<SrcRange>) {
    override fun toString(): String = "@${name.text}(${args.joinToString { it.text }})"
}