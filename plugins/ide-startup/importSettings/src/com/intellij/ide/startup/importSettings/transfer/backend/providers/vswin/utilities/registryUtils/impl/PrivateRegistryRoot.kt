// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.utilities.registryUtils.impl

import com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils.WindowsRegistryErrorTypes
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils.WindowsRegistryException
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils.impl.RegistryRoot
import com.intellij.util.system.WindowsRegistry
import com.jetbrains.rd.util.lifetime.Lifetime
import java.nio.file.Path

class PrivateRegistryRoot private constructor(private val file: Path, lifetime: Lifetime) : RegistryRoot(openPrivateRegistry(file), lifetime) {
    companion object {
        private const val ERROR_SUCCESS = 0
        private const val ERROR_SHARING_VIOLATION = 32
        private const val ERROR_LOCK_VIOLATION = 33
        private const val ERROR_INVALID_NAME = 123

        private val openedRegFiles = mutableMapOf<Path, PrivateRegistryRoot>()
        fun getOrCreate(file: Path, lifetime: Lifetime): PrivateRegistryRoot {
            return openedRegFiles.getOrPut(file) {
                PrivateRegistryRoot(file, lifetime)
            }
        }

        private fun getWindowsErrorText(winCode: Int): String {
            return when (winCode) {
                ERROR_SHARING_VIOLATION -> "The process cannot access the file because it is being used by another process."
                ERROR_LOCK_VIOLATION -> "The process cannot access the file because another process has locked a portion of the file."
                ERROR_INVALID_NAME -> "The filename, directory name, or volume label syntax is incorrect."
                ERROR_SUCCESS -> "Operation completed successfully."

                else -> "Unknown Windows error: $winCode"
            }
        }

        private fun openPrivateRegistry(file: Path): WindowsRegistry.Key {
            val key = try {
                WindowsRegistry.Key.loadAppKey(file)
            }
            catch (t: WindowsRegistry.RegistryException) {
                throw WindowsRegistryException(getWindowsErrorText(t.errorCode), WindowsRegistryErrorTypes.CORRUPTED)
            }

            try {
                if (key.subKeys().isEmpty()) {
                    key.close()
                    throw WindowsRegistryException("The hive has no subkeys, possibly a bad file",
                        WindowsRegistryErrorTypes.CORRUPTED)
                }
            }
            catch (_: WindowsRegistry.RegistryException) {
                key.close()
                throw WindowsRegistryException("Failed to query info key, registry is corrupted",
                    WindowsRegistryErrorTypes.CORRUPTED)
            }

            return key
        }
    }

    init {
        lifetime.onTermination {
            openedRegFiles.remove(file)
        }
    }
}
