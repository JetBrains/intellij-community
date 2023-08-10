interface <lineMarker descr="Is implemented by SkipSupportImpl SkipSupportWithDefaults Press ... to navigate">SkipSupport</lineMarker> {
    fun <lineMarker descr="Is implemented in SkipSupportWithDefaults Press ... to navigate">skip</lineMarker>()
}

public interface <lineMarker descr="Is implemented by SkipSupportImpl Press ... to navigate">SkipSupportWithDefaults</lineMarker> : SkipSupport {
    override fun <lineMarker descr="Implements function in SkipSupport Press ... to navigate">skip</lineMarker>() {}
}

open class SkipSupportImpl : SkipSupportWithDefaults