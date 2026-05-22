package ru.adelf.idea.dotenv.util

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SimpleModificationTracker
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
class EnvironmentVariablesUsagesProvidersModificationTracker(coroutineScope: CoroutineScope) : SimpleModificationTracker() {

    init {
        EnvironmentVariablesProviderUtil.addEnvVariablesUsagesProvidersChangeListener(coroutineScope, ::incModificationCount)
    }

    companion object {
        @JvmStatic
        fun getInstance(): EnvironmentVariablesUsagesProvidersModificationTracker = service()
    }

}
