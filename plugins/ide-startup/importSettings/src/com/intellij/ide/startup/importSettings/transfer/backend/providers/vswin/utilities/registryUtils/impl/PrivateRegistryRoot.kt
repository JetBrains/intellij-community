// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.utilities.registryUtils.impl

import com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils.WindowsRegistryErrorTypes
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils.WindowsRegistryException
import com.intellij.ide.startup.importSettings.providers.vswin.utilities.registryUtils.impl.RegistryRoot
import com.jetbrains.rd.util.lifetime.Lifetime
import com.sun.jna.platform.win32.*
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class PrivateRegistryRoot private constructor(private val file: Path, lifetime: Lifetime) : RegistryRoot(openPrivateRegistry(file), lifetime) {
    companion object {
        private val openedRegFiles = mutableMapOf<Path, PrivateRegistryRoot>()
        fun getOrCreate(file: Path, lifetime: Lifetime): PrivateRegistryRoot {
            return openedRegFiles.getOrPut(file) {
                PrivateRegistryRoot(file, lifetime)
            }
        }

        private fun getWindowsErrorText(winCode: Int): String {
            return when (winCode) {
                W32Errors.ERROR_SHARING_VIOLATION -> "The process cannot access the file because it is being used by another process."
                W32Errors.ERROR_LOCK_VIOLATION -> "The process cannot access the file because another process has locked a portion of the file."
                W32Errors.ERROR_INVALID_NAME -> "The filename, directory name, or volume label syntax is incorrect."
                W32Errors.ERROR_SUCCESS -> "Operation completed successfully."

                else -> "Unknown Windows error: $winCode"
            }
        }

        private fun openPrivateRegistry(file: Path): WinReg.HKEY {
            val phKey = try {
                Advapi32Util.registryLoadAppKey(file.absolutePathString(), WinNT.KEY_ALL_ACCESS, 0)
            }
            catch (t: Win32Exception) {
                throw WindowsRegistryException(getWindowsErrorText(t.errorCode), WindowsRegistryErrorTypes.CORRUPTED)
            }

            try {
                val len = Advapi32Util.registryQueryInfoKey(phKey.value, 0).lpcMaxSubKeyLen.value

                if (len == 0) {
                    throw WindowsRegistryException("lpcMaxSubKeyLen == 0, possibly a bad file",
                        WindowsRegistryErrorTypes.CORRUPTED)
                }
            }
            catch (_: Win32Exception) {
                throw WindowsRegistryException("Failed to query info key, registry is corrupted",
                    WindowsRegistryErrorTypes.CORRUPTED)
            }

            return phKey.value
        }
    }

    init {
        lifetime.onTermination {
            openedRegFiles.remove(file)
        }
    }
}