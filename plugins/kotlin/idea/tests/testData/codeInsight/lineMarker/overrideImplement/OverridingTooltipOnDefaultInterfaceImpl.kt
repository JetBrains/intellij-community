interface <lineMarker descr="Is implemented by SkipSupportImpl SkipSupportImpl1 SkipSupportWithDefaults Press ... to navigate">SkipSupport</lineMarker> {
    fun <lineMarker descr="Is implemented in SkipSupportImpl1 SkipSupportWithDefaults Press ... to navigate">skip</lineMarker>()
}

public interface <lineMarker descr="Is implemented by SkipSupportImpl SkipSupportImpl1 Press ... to navigate">SkipSupportWithDefaults</lineMarker> : SkipSupport {
    override fun <lineMarker descr="Implements function in SkipSupport Press ... to navigate"><lineMarker descr="Is overridden in SkipSupportImpl1 Press ... to navigate">skip</lineMarker></lineMarker>() {}
}

public interface SkipSupportImpl1 : SkipSupportWithDefaults {
    override fun <lineMarker descr="Overrides function in SkipSupportWithDefaults Press ... to navigate">skip</lineMarker>() {}
}

open class SkipSupportImpl : SkipSupportWithDefaults