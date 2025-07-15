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
import com.intellij.openapi.project.modules
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.config.JpsPluginSettings
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.SettingConstants
import org.jetbrains.kotlin.config.SettingConstants.KOTLIN_JPS_PLUGIN_SETTINGS_SECTION
import org.jetbrains.kotlin.config.toKotlinVersion
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.base.plugin.KotlinCompilerVersionProvider
import org.jetbrains.kotlin.idea.base.util.sdk
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings.Companion.getInstance
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings.Companion.readFromKotlincXmlOrIpr
import java.nio.file.Path

@Service(Service.Level.PROJECT)
@State(name = KOTLIN_JPS_PLUGIN_SETTINGS_SECTION, storages = [(Storage(SettingConstants.KOTLIN_COMPILER_SETTINGS_FILE))])
class KotlinJpsPluginSettings(project: Project) : BaseKotlinCompilerSettings<JpsPluginSettings>(project) {
    override fun createSettings() = JpsPluginSettings()

    fun setVersion(jpsVersion: String, externalSystemId: String = "") {
        if (jpsVersion == settings.version) return
        update {
            version = jpsVersion
            this.externalSystemId = externalSystemId
        }
    }

    override fun getState(): Element {
        // We never want to save the bundled JPS version in the kotlinc.xml, so we filter it out here.
        if (settings.version.contains("-release")) {
            update {
                version = ""
            }
        }
        return super.getState()
    }

    override fun onStateDeserialized(state: JpsPluginSettings) {
        // Internal JPS versions are not published to Maven central, so we do not want to load them here as they
        // cannot be downloaded.
        // The empty string defaults to use the bundled JPS version, which is what the user likely expects in this case.
        if (state.version.contains("-release")) {
            state.version = ""
        }
    }

    fun dropExplicitVersion(): Unit = setVersion("")

    companion object {
        private val MIN_KOTLIN_VERSION_JDK_25 = IdeKotlinVersion.get("2.1.10").kotlinVersion

        // Use bundled by default because this will work even without internet connection
        @JvmStatic
        val rawBundledVersion: String get() = bundledVersion.rawVersion

        // Use stable 1.6.21 for outdated compiler versions in order to work with old LV settings
        @JvmStatic
        val fallbackVersionForOutdatedCompiler: String get() = "1.7.22"

        @JvmStatic
        val bundledVersion: IdeKotlinVersion get() = KotlinPluginLayout.standaloneCompilerVersion

        @JvmStatic
        val jpsMinimumSupportedVersion: KotlinVersion = IdeKotlinVersion.get("1.7.0").kotlinVersion

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
         * @param project The project to check for
         * @param fromFile true if [jpsVersion] come from kotlin.xml
         * @return error message if [jpsVersion] is not valid
         */
        @Nls
        fun checkJpsVersion(jpsVersion: String, project: Project, fromFile: Boolean = false): UnsupportedJpsVersionError? {
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

            // There was a major bug because the `JavaVersion` enum was only defined until Java 25 and caused exceptions with
            // larger versions.
            // The K1 compiler (and up to Kotlin 2.1.10) uses this enum and will throw a compilation error if used with JDK 25+.
            // See: KTIJ-34861
            for (module in project.modules) {
                // We check each module independently here because different modules can use different Kotlin versions and JDK versions
                // i.e. there could be a case of a project that uses JDK 25 in some module but only old Kotlin version in others.
                val jdkVersion = module.sdk?.let { sdk -> JavaSdk.getInstance().getVersion(sdk) } ?: continue
                val moduleKotlinVersion = KotlinCompilerVersionProvider.getVersion(module)?.kotlinVersion ?: parsedKotlinVersion

                if (jdkVersion.isAtLeast(JavaSdkVersion.JDK_25) && moduleKotlinVersion < MIN_KOTLIN_VERSION_JDK_25) {
                    return IncompatibleJdkVersion(
                        KotlinBasePluginBundle.message(
                            "kotlin.jps.jdk.unsupported.message",
                            jpsVersion,
                            MIN_KOTLIN_VERSION_JDK_25.toString(),
                        )
                    )
                }
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
            return when (val error = checkJpsVersion(version, project, fromFile = true)) {
                is OutdatedCompilerVersion -> fallbackVersionForOutdatedCompiler

                is NewCompilerVersion, is ParsingError, is IncompatibleJdkVersion -> {
                    onUnsupportedVersion(error.message)
                    null
                }

                null -> version
            }
        }

        /**
         * @param isDelegatedToExtBuild `true` if compiled with Gradle/Maven. `false` if compiled with JPS
         */
        fun importKotlinJpsVersionFromExternalBuildSystem(
            project: Project,
            rawVersion: String,
            isDelegatedToExtBuild: Boolean,
            externalSystemId: String
        ) {
            val instance = getInstance(project)
            val externalSystemIdValidated = validateExternalSystemId(externalSystemId)
            if (rawVersion == rawBundledVersion) {
                runInEdt {
                    runWriteAction {
                        instance.setVersion(rawVersion, externalSystemIdValidated)
                    }
                }
                return
            }

            val error = checkJpsVersion(rawVersion, project)
            val version = when (error) {
                is OutdatedCompilerVersion -> fallbackVersionForOutdatedCompiler
                is NewCompilerVersion, is ParsingError, is IncompatibleJdkVersion -> rawBundledVersion
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

                is IncompatibleJdkVersion -> {
                    showNotificationUnsupportedJdkVersion(project, error.message)
                    return
                }

                null, is OutdatedCompilerVersion -> Unit
            }

            if (!shouldImportKotlinJpsPluginVersionFromExternalBuildSystem(IdeKotlinVersion.get(version))) {
                instance.dropExplicitVersion()
                return
            }

            if (instance.settings.externalSystemId.isNotEmpty() && externalSystemIdValidated != instance.settings.externalSystemId) {
                if (IdeKotlinVersion.get(version).compare(instance.settings.version) < 0) {
                    return
                }
            }

            if (!isDelegatedToExtBuild) {
                downloadKotlinJpsInBackground(project, version)
            }

            runInEdt {
                runWriteAction {
                    instance.setVersion(version, externalSystemIdValidated)
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

        private fun validateExternalSystemId(externalSystemId: String): String =
            if (Registry.getInstance().isLoaded && Registry.`is`("kotlin.jps.cache.external.system.id")) externalSystemId else ""
    }
}

sealed class UnsupportedJpsVersionError(val message: @Nls(capitalization = Nls.Capitalization.Sentence) String)
class ParsingError(message: @Nls(capitalization = Nls.Capitalization.Sentence) String) : UnsupportedJpsVersionError(message)
class OutdatedCompilerVersion(message: @Nls(capitalization = Nls.Capitalization.Sentence) String) : UnsupportedJpsVersionError(message)
class NewCompilerVersion(message: @Nls(capitalization = Nls.Capitalization.Sentence) String) : UnsupportedJpsVersionError(message)
class IncompatibleJdkVersion(message: @Nls(capitalization = Nls.Capitalization.Sentence) String) : UnsupportedJpsVersionError(message)

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

private fun showNotificationUnsupportedJdkVersion(
    project: Project,
    @NlsContexts.NotificationContent content: String,
) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("Kotlin JPS plugin")
        .createNotification(
            KotlinBasePluginBundle.message("kotlin.jps.jdk.unsupported.title"),
            content,
            NotificationType.ERROR
        )
        .setImportant(true)
        .notify(project)
}
