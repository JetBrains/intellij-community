package ml.intellij.nlc.local.utils

import com.github.benmanes.caffeine.cache.Caffeine
import java.util.concurrent.TimeUnit

internal object Caching {
    inline fun <reified K, reified V> default(total: Long = 100, minutes: Long = 5) = Caffeine.newBuilder()
        .maximumSize(total)
        .expireAfterAccess(minutes, TimeUnit.MINUTES)
        .build<K, V>()
}
