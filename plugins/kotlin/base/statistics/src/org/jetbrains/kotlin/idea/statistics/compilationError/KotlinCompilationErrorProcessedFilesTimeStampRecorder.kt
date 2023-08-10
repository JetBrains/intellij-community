// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.statistics.compilationError

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.hashToHexString
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Service(Service.Level.PROJECT)
@State(
    name = "KotlinCompilationErrorFileTimeStamps",
    storages = [Storage(StoragePathMacros.CACHE_FILE)],
    reportStatistic = false,
    reloadable = false,
)
@ApiStatus.Internal
class KotlinCompilationErrorProcessedFilesTimeStampRecorder :
    PersistentStateComponentWithModificationTracker<KotlinCompilationErrorProcessedFilesTimeStampRecorder.MyState> {

    override fun initializeComponent() = lock.write {
        if (dropOutdatedTimestamps()) {
            tracker.incModificationCount()
        }
    }

    private val lock = ReentrantReadWriteLock()
    private var state = MyState()
    private val tracker = SimpleModificationTracker()
    override fun getState(): MyState = lock.read { MyState(HashMap(state.timestamps)) }
    override fun getStateModificationCount(): Long = tracker.modificationCount

    override fun loadState(state: MyState) = lock.write {
        this.state = state
    }

    class MyState(
        @XMap
        @JvmField
        val timestamps: MutableMap<Key, Long> = HashMap()
    ) : BaseState() {
        data class Key(
            // Use md5 instead of the actual file path to make local storage smaller. Users' paths can be huge
            @Tag("filePathMd5")
            val filePathMd5: String,
            @Tag("compilationErrorId")
            val compilationErrorId: String
        )
    }

    fun keepOnlyIfHourPassedAndRecordTimestamps(vFile: VirtualFile, compilationErrorIds: List<String>): List<String> {
        if (compilationErrorIds.isEmpty()) return emptyList()
        val hash = pathMd5Hash(vFile)
        lock.write {
            val currentTime = System.currentTimeMillis()
            val result = compilationErrorIds.asSequence()
                .distinct() /* `isHourPassed` + `recordTimestamp` don't allow the compilation error to happen for the
                               second time in this pipeline, so `distinct` is just a small optimization */
                .filter { isHourPassed(hash, currentTime, it) }
                .onEach { recordTimestamp(hash, currentTime, it) }
                .toList()
            if (dropOutdatedTimestamps(currentTime) || result.isNotEmpty()) {
                tracker.incModificationCount()
            }
            return result
        }
    }

    private fun isHourPassed(md5Hash: String, currentTime: Long, compilationErrorId: String): Boolean {
        val fileTime = state.timestamps[MyState.Key(md5Hash, compilationErrorId)] ?: return true
        return currentTime.isHourPassedSince(fileTime)
    }

    private fun recordTimestamp(md5Hash: String, currentTime: Long, compilationErrorId: String) {
        state.timestamps[MyState.Key(md5Hash, compilationErrorId)] = currentTime
    }

    private fun dropOutdatedTimestamps(currentTime: Long = System.currentTimeMillis()): Boolean =
        state.timestamps.values.removeIf { currentTime.isHourPassedSince(it) }

    companion object {
        fun getInstance(project: Project): KotlinCompilationErrorProcessedFilesTimeStampRecorder = project.service()
    }
}

private fun pathMd5Hash(virtualFile: VirtualFile): String = hashToHexString(virtualFile.path, DigestUtil.md5())

private fun Long.isHourPassedSince(lastTime: Long): Boolean = TimeUnit.MILLISECONDS.toHours(this - lastTime) >= 1