// IGNORE_FIR
// ISSUE: KT-42263

interface TestModule
sealed class ResultingArtifact {
    abstract class Source<R : Source<R>> : ResultingArtifact() {
        abstract val frontendKind: FrontendKind<R>
    }
}
class ClassicFrontendSourceArtifacts : ResultingArtifact.Source<ClassicFrontendSourceArtifacts>() {
    override val frontendKind: FrontendKind<ClassicFrontendSourceArtifacts>
        get() = FrontendKind.ClassicFrontend
}
sealed class FrontendKind<R : ResultingArtifact.Source<R>> {
    object ClassicFrontend : FrontendKind<ClassicFrontendSourceArtifacts>()
}
abstract class DependencyProvider {
    abstract fun <R : ResultingArtifact.Source<R>> registerSourceArtifact(artifact: R)
}
fun test(dependencyProvider: DependencyProvider, artifact: ResultingArtifact.Source<*>) {
    dependencyProvider.registerSourceArtifact(artifact) // <- uncomment this and see exception
}
