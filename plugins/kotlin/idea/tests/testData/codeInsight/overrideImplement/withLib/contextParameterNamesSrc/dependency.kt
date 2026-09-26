package dependency

class CheckerContext

class DiagnosticReporter

interface Checker<D> {
    context(checkerContext: CheckerContext, diagnosticReporter: DiagnosticReporter)
    fun check(d: D)
}

typealias AChecker = Checker<String>