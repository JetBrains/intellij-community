// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.*
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.DefaultCodegenFactory
import org.jetbrains.kotlin.codegen.KotlinCodegenFacade
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.util.InlineFunctionAnalyzer
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.source.PsiSourceFile
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * @param shouldStubUnboundIrSymbols Whether unbound IR symbols should be stubbed instead of linked. This should be enabled if the [file]
 * could refer to symbols defined in another file of the same module. Such symbols are not compiled by [KotlinCompilerIde] (only [file]
 * itself is compiled) and cannot be linked from a dependency. [shouldStubUnboundIrSymbols] only has an effect if [JVMConfigurationKeys.IR]
 * is set to `true` in [initialConfiguration].
 */
class KotlinCompilerIde(
    private val file: KtFile,
    private val initialConfiguration: CompilerConfiguration = getDefaultCompilerConfiguration(file),
    private val classBuilderFactory: ClassBuilderFactory = ClassBuilderFactories.BINARIES,
    private val resolutionFacadeProvider: (KtFile) -> ResolutionFacade? = { file.getResolutionFacade() },
    private val classFilesOnly: Boolean = false,
    private val shouldStubUnboundIrSymbols: Boolean = false,
) {
    companion object {
        private fun getDefaultCompilerConfiguration(file: KtFile): CompilerConfiguration {
            return CompilerConfiguration().apply {
                languageVersionSettings = file.languageVersionSettings
            }
        }
    }

    class CompiledFile(val path: String, val bytecode: ByteArray)

    fun compileToDirectory(destination: File) {
        destination.mkdirs()

        val state = compile() ?: return

        try {
            for (outputFile in getFiles(state)) {
                val target = File(destination, outputFile.relativePath)
                (target.parentFile ?: error("Can't find parent for file $target")).mkdirs()
                target.writeBytes(outputFile.asByteArray())
            }
        } finally {
            state.destroy()
        }
    }

    fun compileToJar(destination: File) {
        destination.outputStream().buffered().use { os ->
            ZipOutputStream(os).use { zos ->
                val state = compile()

                if (state != null) {
                    try {
                        for (outputFile in getFiles(state)) {
                            zos.putNextEntry(ZipEntry(outputFile.relativePath))
                            zos.write(outputFile.asByteArray())
                            zos.closeEntry()
                        }
                    } finally {
                        state.destroy()
                    }
                }
            }
        }
    }

    fun compileToBytecode(): List<CompiledFile> {
        val state = compile() ?: return emptyList()

        try {
            return getFiles(state).map { CompiledFile(it.relativePath, it.asByteArray()) }
        } finally {
            state.destroy()
        }
    }

    fun compile(): GenerationState? = compileImpl(traceClassFileOrigins = false)?.first

    fun compileTracingOrigin(): Pair<GenerationState, ClassFileOrigins>? = compileImpl(traceClassFileOrigins = true)

    private fun compileImpl(traceClassFileOrigins: Boolean): Pair<GenerationState, ClassFileOrigins>? {
        val project = file.project
        val platform = file.platform

        if (!platform.isCommon() && !platform.isJvm()) return null

        val resolutionFacade = resolutionFacadeProvider(file) ?: return null
        val configuration = initialConfiguration.copy().apply {
            put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)
        }
        val isIrBackend = configuration.getBoolean(JVMConfigurationKeys.IR)

        // The binding context needs to be built from all files with reachable inline functions, as such files may contain classes whose
        // descriptors must be available in the binding context for the IR backend. Note that the full bytecode is only generated for
        // `file` because of filtering in `generateClassFilter`, while only select declarations from other files are generated if needed
        // by the backend.
        val inlineAnalyzer = InlineFunctionAnalyzer(
            resolutionFacade,
            analyzeOnlyReifiedInlineFunctions = configuration.getBoolean(CommonConfigurationKeys.DISABLE_INLINE),
        ).apply { analyze(file) }
        val toProcess = inlineAnalyzer.allFiles()
        val bindingContext = resolutionFacade.analyzeWithAllCompilerChecks(toProcess).bindingContext

        // The IR backend will try to regenerate object literals defined in inline functions from generated class files during inlining.
        // Hence, we need to be aware of which object declarations are defined in the relevant inline functions.
        val inlineObjectDeclarations = when {
            isIrBackend -> inlineAnalyzer.inlineObjectDeclarations()
            else -> setOf()
        }
        val inlineObjectDeclarationFiles = inlineObjectDeclarations.mapTo(mutableSetOf()) { it.containingKtFile }

        val generateClassFilter = object : GenerationState.GenerateClassFilter() {
            override fun shouldGeneratePackagePart(ktFile: KtFile): Boolean {
                return file === ktFile || inlineObjectDeclarationFiles.contains(ktFile)
            }

            override fun shouldAnnotateClass(processingClassOrObject: KtClassOrObject): Boolean {
                return true
            }

            override fun shouldGenerateClass(processingClassOrObject: KtClassOrObject): Boolean {
                return processingClassOrObject.containingKtFile === file ||
                       processingClassOrObject is KtObjectDeclaration && inlineObjectDeclarations.contains(processingClassOrObject)
            }

            override fun shouldGenerateScript(script: KtScript): Boolean {
                return script.containingKtFile === file
            }

            override fun shouldGenerateCodeFragment(script: KtCodeFragment) = false
        }

        val codegenFactory = when {
            isIrBackend -> createJvmIrCodegenFactory(configuration)
            else -> DefaultCodegenFactory
        }

        val originTracingClassBuilderFactory = if (traceClassFileOrigins) OriginTracingClassBuilderFactory(classBuilderFactory) else null

        val state = GenerationState.Builder(
            project,
            originTracingClassBuilderFactory ?: classBuilderFactory,
            resolutionFacade.moduleDescriptor,
            bindingContext,
            toProcess,
            configuration,
        ).generateDeclaredClassFilter(generateClassFilter)
            .codegenFactory(codegenFactory)
            .build()

        KotlinCodegenFacade.compileCorrectFiles(state)
        return Pair(state, originTracingClassBuilderFactory?.classFileOrigins ?: emptyMap())
    }

    /**
     * Creates a [JvmIrCodegenFactory] that stubs unbound symbols if [shouldStubUnboundIrSymbols] is enabled.
     */
    private fun createJvmIrCodegenFactory(compilerConfiguration: CompilerConfiguration): JvmIrCodegenFactory {
        val jvmGeneratorExtensions = if (shouldStubUnboundIrSymbols) {
            object : JvmGeneratorExtensionsImpl(compilerConfiguration) {
                override fun getContainerSource(descriptor: DeclarationDescriptor): DeserializedContainerSource? {
                    // Stubbed top-level function IR symbols (from other source files in the module) require a parent facade class to be
                    // generated, which requires a container source to be provided. Without a facade class, function IR symbols will have
                    // an `IrExternalPackageFragment` parent, which trips up code generation during IR lowering.
                    val psiSourceFile =
                        descriptor.toSourceElement.containingFile as? PsiSourceFile ?: return super.getContainerSource(descriptor)
                    return FacadeClassSourceShimForFragmentCompilation(psiSourceFile)
                }
            }
        } else JvmGeneratorExtensionsImpl(compilerConfiguration)

        val ideCodegenSettings = JvmIrCodegenFactory.IdeCodegenSettings(
          shouldStubAndNotLinkUnboundSymbols = shouldStubUnboundIrSymbols,
          shouldDeduplicateBuiltInSymbols = shouldStubUnboundIrSymbols,

          // Because the file to compile may be contained in a "common" multiplatform module, an `expect` declaration doesn't necessarily
          // have an obvious associated `actual` symbol. `shouldStubOrphanedExpectSymbols` generates stubs for such `expect` declarations.
          shouldStubOrphanedExpectSymbols = true,

          // Likewise, the file to compile may be contained in a "platform" multiplatform module, where the `actual` declaration is
          // referenced in the symbol table automatically, but not its `expect` counterpart, because it isn't contained in the files to
          // compile. `shouldReferenceUndiscoveredExpectSymbols` references such `expect` symbols in the symbol table so that they can
          // subsequently be stubbed.
          shouldReferenceUndiscoveredExpectSymbols = true,
        )

        return JvmIrCodegenFactory(
          compilerConfiguration,
          PhaseConfig(jvmPhases),
          jvmGeneratorExtensions = jvmGeneratorExtensions,
          ideCodegenSettings = ideCodegenSettings,
        )
    }

    private fun getFiles(state: GenerationState): List<OutputFile> {
        val allFiles = state.factory.asList()
        if (classFilesOnly) {
            return allFiles.filter { it.relativePath.endsWith(".class") }
        }
        return allFiles
    }
}