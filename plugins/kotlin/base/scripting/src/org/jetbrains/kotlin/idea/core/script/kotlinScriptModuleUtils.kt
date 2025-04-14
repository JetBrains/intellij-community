package org.jetbrains.kotlin.idea.core.script

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

const val KOTLIN_SCRIPTS_MODULE_NAME: String = "kotlin.scripts"

open class KotlinScriptEntitySource(override val virtualFileUrl: VirtualFileUrl?) : EntitySource
