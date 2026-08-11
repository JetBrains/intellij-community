// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.shared.definition

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import java.io.File
import javax.swing.Icon
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.experimental.util.PropertiesCollection.Key


val IdeScriptCompilationConfigurationKeys.jdkSupplier: Key<(VirtualFile) -> File?> by PropertiesCollection.key(
    getDefaultValue = {
        { get(ScriptCompilationConfiguration.jvm.jdkHome) }
    }
)

val IdeScriptCompilationConfigurationKeys.kotlinScriptDefinitionInlayHint: Key<((ScriptCompilationConfiguration) -> String)?>
        by PropertiesCollection.key({ configuration ->
            val title = configuration[ScriptCompilationConfiguration.ide.kotlinScriptTemplate]?.title
            val displayName = title ?: ".${configuration[ScriptCompilationConfiguration.fileExtension] ?: "kts"}"
            KotlinBaseScriptingBundle.message("hints.codevision.script.definition", displayName)
        })

val IdeScriptCompilationConfigurationKeys.kotlinScriptTemplate: Key<KotlinScriptTemplate> by PropertiesCollection.key()

val IdeScriptCompilationConfigurationKeys.reloadable: Key<Boolean> by PropertiesCollection.key(defaultValue = true)

data class KotlinScriptTemplate(var id: String = "") {
    @Nls
    var title: String = ""
    var templateName: String = "Kotlin Script"
    var icon: Icon = KotlinIcons.SCRIPT

    @Nls
    var description: String = ""
}


fun ScriptCompilationConfiguration.Builder.kotlinScriptTemplate(init: KotlinScriptTemplate.() -> Unit) {
    ide.kotlinScriptTemplate(KotlinScriptTemplate().apply(init))
}