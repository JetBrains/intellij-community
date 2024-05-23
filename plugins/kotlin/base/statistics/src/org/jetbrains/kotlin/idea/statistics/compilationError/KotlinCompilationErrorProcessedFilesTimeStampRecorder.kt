// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics.compilationError

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.hashToHexString
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
@State(
    name = "KotlinCompilationErrorFileTimeStamps",
    storages = [Storage(StoragePathMacros.CACHE_FILE)],
    reportStatistic = false,
    reloadable = false,
)
internal class KotlinCompilationErrorProcessedFilesTimeStampRecorder :
    SerializablePersistentStateComponent<KotlinCompilationErrorProcessedFilesTimeStampRecorder.MyState>(EMPTY_STATE) {

    override fun initializeComponent() {
        val currentTime = System.currentTimeMillis()
        if (state.timestamps.values.any { currentTime.isHourPassedSince(it) }) {
            updateState { state ->
                val timestamps = HashMap(state.timestamps)
                dropOutdatedTimestamps(currentTime = currentTime, timestamps = timestamps)
                if (timestamps.isEmpty()) EMPTY_STATE else MyState(timestamps = timestamps)
            }
        }
    }

    @Serializable
    class MyState(
        @JvmField val timestamps: Map<String, Long> = emptyMap()
    )

    fun keepOnlyIfHourPassedAndRecordTimestamps(vFile: VirtualFile, compilationErrorIds: List<String>): List<String> {
        if (compilationErrorIds.isEmpty()) return emptyList()
        val hash = pathMd5Hash(vFile)
        val result = mutableListOf<String>()
        updateState { state ->
            val timestamps = HashMap(state.timestamps)
            val currentTime = System.currentTimeMillis()
            result.clear()
            compilationErrorIds.asSequence()
                .distinct() /* `isHourPassed` + `recordTimestamp` don't allow the compilation error to happen for the
                               second time in this pipeline, so `distinct` is just a small optimization */
                .filter { isHourPassed(md5Hash = hash, currentTime = currentTime, compilationErrorId = it, timestamps = timestamps) }
                .onEach { recordTimestamp(md5Hash = hash, currentTime = currentTime, compilationErrorId = it, timestamps = timestamps) }
                .toCollection(result)
            dropOutdatedTimestamps(currentTime, timestamps)
            if (timestamps.isEmpty()) EMPTY_STATE else MyState(timestamps = timestamps)
        }
        return result
    }

    companion object {
        fun getInstance(project: Project): KotlinCompilationErrorProcessedFilesTimeStampRecorder = project.service()
    }
}

private val EMPTY_STATE = KotlinCompilationErrorProcessedFilesTimeStampRecorder.MyState()

private fun isHourPassed(md5Hash: String, currentTime: Long, compilationErrorId: String, timestamps: Map<String, Long>): Boolean {
    val fileTime = timestamps[createCompositeKey(md5Hash, compilationErrorId)] ?: return true
    return currentTime.isHourPassedSince(fileTime)
}

// Use md5 instead of the actual file path to make local storage smaller.
// Users' paths can be huge.
// Key - combination of filePathMd5 + compilationErrorId, to avoid non-primitive key.
private fun createCompositeKey(md5Hash: String, compilationErrorId: String) = "$md5Hash-$compilationErrorId"

private fun recordTimestamp(md5Hash: String, currentTime: Long, compilationErrorId: String, timestamps: MutableMap<String, Long>) {
    timestamps[createCompositeKey(md5Hash, compilationErrorId)] = currentTime
}

private fun dropOutdatedTimestamps(currentTime: Long, timestamps: MutableMap<String, Long>): Boolean {
    return timestamps.values.removeIf { currentTime.isHourPassedSince(it) }
}

private fun pathMd5Hash(virtualFile: VirtualFile): String = hashToHexString(virtualFile.path, DigestUtil.md5())

private fun Long.isHourPassedSince(lastTime: Long): Boolean = TimeUnit.MILLISECONDS.toHours(this - lastTime) >= 1