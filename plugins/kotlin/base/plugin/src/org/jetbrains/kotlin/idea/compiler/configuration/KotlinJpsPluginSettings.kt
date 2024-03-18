// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compiler.configuration

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.xmlb.XmlSerializer
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.JpsPluginSettings
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_JPS_PLUGIN_SETTINGS_SECTION
import org.jetbrains.kotlin.config.toKotlinVersion
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import java.nio.file.Path

@Service(Service.Level.PROJECT)
@State(name = KOTLIN_JPS_PLUGIN_SETTINGS_SECTION, storages = [(Storage(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE))])
class KotlinJpsPluginSettings(project: Project) : BaseKotlinCompilerSettings<JpsPluginSettings>(project) {
    override fun createSettings() = JpsPluginSettings()

    fun setVersion(jpsVersion: String) {
        if (jpsVersion == settings.version) return
        update { version = jpsVersion }
    }

    fun dropExplicitVersion(): Unit = setVersion("")

    companion object {
        // Use bundled by default because this will work even without internet connection
        @JvmStatic
        val rawBundledVersion: String get() = bundledVersion.rawVersion

        // Use stable 1.6.21 for outdated compiler versions in order to work with old LV settings
        @JvmStatic
        val fallbackVersionForOutdatedCompiler: String get() = "1.6.21"

        @JvmStatic
        val bundledVersion: IdeKotlinVersion get() = KotlinPluginLayout.standaloneCompilerVersion

        @JvmStatic
        val jpsMinimumSupportedVersion: KotlinVersion = IdeKotlinVersion.get("1.6.0").kotlinVersion

        @JvmStatic
        val jpsMaximumSupportedVersion: KotlinVersion = LanguageVersion.values().last().toKotlinVersion()

        fun validateSettings(project: Project) {
            val jpsPluginSettings = getInstance(project)

            if (jpsPluginSettings.settings.version.isEmpty() && bundledVersion.buildNumber == null) {
                // Encourage user to specify desired Kotlin compiler version in project settings for sake of reproducible builds
                // it's important to trigger `.idea/kotlinc.xml` file creation
                jpsPluginSettings.setVersion(rawBundledVersion)
            }
        }

        fun jpsVersion(project: Project): String = getInstance(project).settings.versionWithFallback

        /**
         * @see readFromKotlincXmlOrIpr
         */
        @JvmStatic
        fun getInstance(project: Project): KotlinJpsPluginSettings = project.service()

        /**
         * @param jpsVersion version to parse
         * @param fromFile true if [jpsVersion] come from kotlin.xml
         * @return error message if [jpsVersion] is not valid
         */
        @Nls
        fun checkJpsVersion(jpsVersion: String, fromFile: Boolean = false): UnsupportedJpsVersionError? {
            val parsedKotlinVersion = IdeKotlinVersion.opt(jpsVersion)?.kotlinVersion
            if (parsedKotlinVersion == null) {
                return ParsingError(
                    if (fromFile) {
                        KotlinBasePluginBundle.message(
                            "failed.to.parse.kotlin.version.0.from.1",
                            jpsVersion,
                            SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE,
                        )
                    } else {
                        KotlinBasePluginBundle.message("failed.to.parse.kotlin.version.0", jpsVersion)
                    }
                )
            }

            if (parsedKotlinVersion < jpsMinimumSupportedVersion) {
                return OutdatedCompilerVersion(
                    KotlinBasePluginBundle.message(
                        "kotlin.jps.compiler.minimum.supported.version.not.satisfied",
                        jpsMinimumSupportedVersion,
                        jpsVersion,
                    )
                )

            }

            if (parsedKotlinVersion > jpsMaximumSupportedVersion) {
                return NewCompilerVersion(
                    KotlinBasePluginBundle.message(
                        "kotlin.jps.compiler.maximum.supported.version.not.satisfied",
                        jpsMaximumSupportedVersion,
                        jpsVersion,
                    )
                )
            }

            return null
        }

        /**
         * Replacement for [getInstance] for cases when it's not possible to use [getInstance] (e.g. project isn't yet initialized).
         *
         * Please, prefer [getInstance] if possible.
         */
        fun readFromKotlincXmlOrIpr(path: Path): JpsPluginSettings? {
            val root = try {
                JDOMUtil.load(path)
            } catch (ex: java.nio.file.NoSuchFileException) {
                return null
            } catch (ex: org.jdom.JDOMException) { // e.g. Unexpected End-of-input in prolog
                return null
            }
            return root.children
                .singleOrNull { it.getAttributeValue("name") == KotlinJpsPluginSettings::class.java.simpleName }
                ?.let { xmlElement ->
                    JpsPluginSettings().apply {
                        XmlSerializer.deserializeInto(this, xmlElement)
                    }
                }
        }

        fun supportedJpsVersion(
            project: Project,
            onUnsupportedVersion: (@Nls(capitalization = Nls.Capitalization.Sentence) String) -> Unit,
        ): String? {
            val version = jpsVersion(project)
            return when (val error = checkJpsVersion(version, fromFile = true)) {
                is OutdatedCompilerVersion -> fallbackVersionForOutdatedCompiler

                is NewCompilerVersion, is ParsingError -> {
                    onUnsupportedVersion(error.message)
                    null
                }

                null -> version
            }
        }

        /**
         * @param isDelegatedToExtBuild `true` if compiled with Gradle/Maven. `false` if compiled with JPS
         */
        fun importKotlinJpsVersionFromExternalBuildSystem(project: Project, rawVersion: String, isDelegatedToExtBuild: Boolean) {
            val instance = getInstance(project)
            if (rawVersion == rawBundledVersion) {
                runInEdt {
                    runWriteAction {
                        instance.setVersion(rawVersion)
                    }
                }
                return
            }

            val error = checkJpsVersion(rawVersion)
            val version = when (error) {
                is OutdatedCompilerVersion -> fallbackVersionForOutdatedCompiler
                is NewCompilerVersion, is ParsingError -> rawBundledVersion
                null -> rawVersion
            }

            if (error != null && !isDelegatedToExtBuild) {
                showNotificationUnsupportedJpsPluginVersion(
                    project,
                    KotlinBasePluginBundle.message("notification.title.unsupported.kotlin.jps.plugin.version"),
                    KotlinBasePluginBundle.message(
                        "notification.content.bundled.version.0.will.be.used.reason.1",
                        version,
                        error.message
                    ),
                )
            }
            when (error) {
                is ParsingError, is NewCompilerVersion -> {
                    instance.dropExplicitVersion()
                    return
                }

                null, is OutdatedCompilerVersion -> Unit
            }

            if (!shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(IdeKotlinVersion.get(version))) {
                instance.dropExplicitVersion()
                return
            }

            if (!isDelegatedToExtBuild) {
                downloadKotlinJpsInBackground(project, version)
            }
            runInEdt {
                runWriteAction {
                    instance.setVersion(version)
                }
            }
        }

        private fun downloadKotlinJpsInBackground(project: Project, version: String) {
            ProgressManager.getInstance().run(
                object : Task.Backgroundable(project, KotlinBasePluginBundle.getMessage("progress.text.downloading.kotlinc.dist"), true) {
                    override fun isHeadless(): Boolean {
                        return false
                    }

                    override fun run(indicator: ProgressIndicator) {
                        KotlinArtifactsDownloader.lazyDownloadMissingJpsPluginDependencies(
                            project,
                            version,
                            indicator,
                            onError = {
                                showNotificationUnsupportedJpsPluginVersion(
                                    project,
                                    KotlinBasePluginBundle.message("kotlin.dist.downloading.failed"),
                                    it,
                                )
                            }
                        )
                    }
                }
            )
        }

        internal fun shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(version: IdeKotlinVersion): Boolean {
            check(jpsMinimumSupportedVersion < IdeKotlinVersion.get("1.7.10").kotlinVersion) {
                "${::shouldImportKotlinJpsPluginVersionFromExternalBuildSystem.name} makes sense when minimum supported version is lower " +
                        "than 1.7.20. If minimum supported version is already 1.7.20 then you can drop this function."
            }
            require(version.kotlinVersion >= jpsMinimumSupportedVersion) {
                "${version.kotlinVersion} is lower than $jpsMinimumSupportedVersion"
            }
            val kt160 = IdeKotlinVersion.get("1.6.0")
            val kt170 = IdeKotlinVersion.get("1.7.0")
            // Until 1.6.0 none of unbundled Kotlin JPS artifacts was published to the Maven Central.
            // In range [1.6.0, 1.7.0] unbundled Kotlin JPS artifacts were published only for release Kotlin versions.
            return version > kt170 || version >= kt160 && version.isRelease && version.buildNumber == null
        }
    }
}

sealed class UnsupportedJpsVersionError(val message: @Nls(capitalization = Nls.Capitalization.Sentence) String)
class ParsingError(message: @Nls(capitalization = Nls.Capitalization.Sentence) String) : UnsupportedJpsVersionError(message)
class OutdatedCompilerVersion(message: @Nls(capitalization = Nls.Capitalization.Sentence) String) : UnsupportedJpsVersionError(message)
class NewCompilerVersion(message: @Nls(capitalization = Nls.Capitalization.Sentence) String) : UnsupportedJpsVersionError(message)

@get:NlsSafe
val JpsPluginSettings.versionWithFallback: String get() = version.ifEmpty { KotlinJpsPluginSettings.rawBundledVersion }

private fun showNotificationUnsupportedJpsPluginVersion(
    project: Project,
    @NlsContexts.NotificationTitle title: String,
    @NlsContexts.NotificationContent content: String,
) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Kotlin JPS plugin")
        .createNotification(title, content, NotificationType.WARNING)
        .setImportant(true)
        .notify(project)
}
