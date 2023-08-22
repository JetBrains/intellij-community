interface <lineMarker descr="Is implemented by SkipSupportImpl SkipSupportWithDefaults Press ... to navigate">SkipSupport</lineMarker> {
    fun <lineMarker descr="Is implemented in SkipSupportImpl SkipSupportWithDefaults Press ... to navigate">skip</lineMarker>(why: String)
    fun <lineMarker descr="Is implemented in SkipSupportWithDefaults Press ... to navigate">skip</lineMarker>()
}

public interface <lineMarker descr="Is implemented by SkipSupportImpl Press ... to navigate">SkipSupportWithDefaults</lineMarker> : SkipSupport {
    override fun <lineMarker descr="Implements function in SkipSupport Press ... to navigate"><lineMarker descr="Is overridden in SkipSupportImpl Press ... to navigate">skip</lineMarker></lineMarker>(why: String) {}

    override fun <lineMarker descr="Implements function in SkipSupport Press ... to navigate">skip</lineMarker>() {
        skip("not given")
    }
}

open class SkipSupportImpl: SkipSupportWithDefaults {
    override fun <lineMarker descr="Overrides function in SkipSupportWithDefaults Press ... to navigate">skip</lineMarker>(why: String) = throw RuntimeException(why)
}

// KT-4428 Incorrect override icon shown for overloaded methods