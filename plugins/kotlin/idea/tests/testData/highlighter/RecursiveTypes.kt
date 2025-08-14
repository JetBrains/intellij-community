// IGNORE_K2
// ISSUE: KT-42263

interface <symbolName descr="null">TestModule</symbolName>
<symbolName descr="null">sealed</symbolName> class <symbolName descr="null">ResultingArtifact</symbolName> {
    <symbolName descr="null">abstract</symbolName> class <symbolName descr="null">Source</symbolName><<symbolName descr="null">R</symbolName> : <symbolName descr="null">Source</symbolName><<symbolName descr="null">R</symbolName>>> : <symbolName descr="null">ResultingArtifact</symbolName>() {
        <symbolName descr="null">abstract</symbolName> val <symbolName descr="null">frontendKind</symbolName>: <symbolName descr="null">FrontendKind</symbolName><<symbolName descr="null">R</symbolName>>
    }
}
class <symbolName descr="null">ClassicFrontendSourceArtifacts</symbolName> : <symbolName descr="null">ResultingArtifact</symbolName>.<symbolName descr="null">Source</symbolName><<symbolName descr="null">ClassicFrontendSourceArtifacts</symbolName>>() {
    <symbolName descr="null">override</symbolName> val <symbolName descr="null">frontendKind</symbolName>: <symbolName descr="null">FrontendKind</symbolName><<symbolName descr="null">ClassicFrontendSourceArtifacts</symbolName>>
        <symbolName descr="null">get</symbolName>() = <symbolName descr="null">FrontendKind</symbolName>.<symbolName descr="null">ClassicFrontend</symbolName>
}
<symbolName descr="null">sealed</symbolName> class <symbolName descr="null">FrontendKind</symbolName><<symbolName descr="null">R</symbolName> : <symbolName descr="null">ResultingArtifact</symbolName>.<symbolName descr="null">Source</symbolName><<symbolName descr="null">R</symbolName>>> {
    object <symbolName descr="null">ClassicFrontend</symbolName> : <symbolName descr="null">FrontendKind</symbolName><<symbolName descr="null">ClassicFrontendSourceArtifacts</symbolName>>()
}
<symbolName descr="null">abstract</symbolName> class <symbolName descr="null">DependencyProvider</symbolName> {
    <symbolName descr="null">abstract</symbolName> fun <<symbolName descr="null">R</symbolName> : <symbolName descr="null">ResultingArtifact</symbolName>.<symbolName descr="null">Source</symbolName><<symbolName descr="null">R</symbolName>>> <symbolName descr="null">registerSourceArtifact</symbolName>(<symbolName descr="null">artifact</symbolName>: <symbolName descr="null">R</symbolName>)
}
fun <symbolName descr="null">test</symbolName>(<symbolName descr="null">dependencyProvider</symbolName>: <symbolName descr="null">DependencyProvider</symbolName>, <symbolName descr="null">artifact</symbolName>: <symbolName descr="null">ResultingArtifact</symbolName>.<symbolName descr="null">Source</symbolName><*>) {
    <symbolName descr="null">dependencyProvider</symbolName>.<symbolName descr="null">registerSourceArtifact</symbolName>(<error descr="[TYPE_MISMATCH] Type mismatch: inferred type is ResultingArtifact.Source<*> but CapturedType(*) was expected"><symbolName descr="null">artifact</symbolName></error>) // <- uncomment this and see exception
}
