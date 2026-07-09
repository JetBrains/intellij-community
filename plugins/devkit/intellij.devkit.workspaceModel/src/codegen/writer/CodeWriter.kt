// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.codegen.writer

import com.intellij.application.options.CodeStyle
import com.intellij.application.options.codeStyle.cache.CodeStyleCachingService
import com.intellij.copyright.withCopyrightUpdateDisabled
import com.intellij.devkit.workspaceModel.CodegenJarLoader
import com.intellij.devkit.workspaceModel.DevKitWorkspaceModelBundle
import com.intellij.devkit.workspaceModel.codegen.writer.CodeWriter.addGeneratedObjModuleFile
import com.intellij.devkit.workspaceModel.metaModel.WorkspaceMetaModelProvider
import com.intellij.lang.LanguageImportStatements
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.FactoryMap
import com.intellij.workspaceModel.codegen.deft.meta.CompiledObjModule
import com.intellij.workspaceModel.codegen.engine.CodeGenerator
import com.intellij.workspaceModel.codegen.engine.GenerationProblem
import com.intellij.workspaceModel.codegen.engine.GenerationResult
import com.intellij.workspaceModel.codegen.engine.GeneratorSettings
import com.intellij.workspaceModel.codegen.engine.ObjClassGeneratedCode
import com.intellij.workspaceModel.codegen.engine.ObjModuleFileGeneratedCode
import com.intellij.workspaceModel.codegen.engine.SKIPPED_TYPES
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.io.JsonReaderEx
import org.jetbrains.io.JsonUtil
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.isPublic
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierType
import org.jetbrains.kotlin.resolve.ImportPath
import java.io.IOException
import java.net.URI
import java.util.ServiceLoader
import java.util.jar.Manifest

private val LOG = logger<CodeWriter>()

object CodeWriter {
  @RequiresBackgroundThread
  suspend fun generate(
    project: Project, module: Module, sourceFolder: VirtualFile,
    processAbstractTypes: Boolean, explicitApiEnabled: Boolean,
    isTestSourceFolder: Boolean, isTestModule: Boolean,
    targetFolderGenerator: () -> VirtualFile?,
    existingTargetFolder: () -> VirtualFile?,
    formatCode: Boolean,
  ) {
    val sourceFilePerObjModule = HashMap<String, VirtualFile>()
    val ktClasses = HashMap<String, KtClassOrObject>()
    readAction {
      VfsUtilCore.processFilesRecursively(sourceFolder) { file ->
        if (file.extension == "kt") {
          val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile?

          ktFile?.declarations?.filterIsInstance<KtClassOrObject>()?.filter { it.name != null }?.forEach { ktClass ->
            val fqName = ktClass.fqName!!.asString()
            val objModuleName = fqName.replace(ktClass.name!!, "").substringBeforeLast(".")

            /**
             *  We find one virtual file for each module.
             *  This is necessary to find the relative path for the generated GeneratedObjModuleFile.
             *  See [addGeneratedObjModuleFile] method.
             */
            sourceFilePerObjModule[objModuleName] = file
            ktClasses[fqName] = ktClass
          }
        }
        return@processFilesRecursively true
      }
    }
    if (ktClasses.isEmpty()) return

    val classLoader = CodegenJarLoader.getInstance(project).getClassLoader()
    val serviceLoader = ServiceLoader.load(CodeGenerator::class.java, classLoader).findFirst()
    if (serviceLoader.isEmpty) error("Can't load generator")

    val codeGenerator = serviceLoader.get()
    if (!codegenApiVersionsAreCompatible(project, codeGenerator)) {
      return
    }

    DumbService.getInstance(project).waitForSmartMode()

    val generatedFiles = ArrayList<KtFile>()

    val commandName = DevKitWorkspaceModelBundle.message("command.name.generate.code.for.workspace.entities.in", sourceFolder.name)

    withCopyrightUpdateDisabled(project) {
      withModalProgress(project, commandName) {
        reportRawProgress { reporter ->
          reporter.text(DevKitWorkspaceModelBundle.message("progress.text.collecting.classes.metadata"))
          val (objModules, metaProblems) = readAction {
            WorkspaceMetaModelProvider().loadObjModules(ktClasses, module, processAbstractTypes, isTestSourceFolder)
          }
          if (metaProblems.isNotEmpty()) {
            WorkspaceCodegenProblemsProvider.getInstance(project).reportMetaProblem(metaProblems)
            return@withModalProgress
          }

          val results = generate(codeGenerator, objModules, explicitApiEnabled, isTestModule)
          val generatedCode = results.flatMap { it.generatedCode }
          val problems = results.flatMap { it.problems }
          WorkspaceCodegenProblemsProvider.getInstance(project).reportProblems(problems)

          if (generatedCode.isEmpty() || problems.any { it.level == GenerationProblem.Level.ERROR }) {
            return@withModalProgress
          }

          val genFolder = existingTargetFolder.invoke() ?: targetFolderGenerator.invoke()
          if (genFolder == null) {
            LOG.info("Generated source folder doesn't exist. Skip processing source folder with path: ${sourceFolder}")
            return@withModalProgress
          }

          reporter.text(DevKitWorkspaceModelBundle.message("progress.text.removing.old.code"))
          writeCommandActionWithGroupId(project, commandName) {
            removeGeneratedCode(genFolder)
          }

          reporter.text(DevKitWorkspaceModelBundle.message("progress.text.writing.code"))

          val importsByFile = FactoryMap.create<KtFile, Imports> { Imports(it.packageFqName.asString()) }
          generatedCode.forEachIndexed { i, code ->
            writeCommandActionWithGroupId(project, commandName) {
              val psiFactory = KtPsiFactory(project)
              reporter.fraction(0.15 + 0.1 * i / generatedCode.size)
              when (code) {
                is ObjModuleFileGeneratedCode ->
                  addGeneratedObjModuleFile(
                    code, generatedFiles, project,
                    sourceFolder, genFolder,
                    sourceFilePerObjModule, importsByFile, psiFactory)
                is ObjClassGeneratedCode ->
                  addGeneratedObjClassFile(
                    code, generatedFiles, project,
                    sourceFolder, genFolder,
                    ktClasses, importsByFile, psiFactory,
                  )
              }
            }
          }
          writeCommandActionWithGroupId(project, commandName) {
            importsByFile.forEach { (file, imports) ->
              addImports(file, imports)
            }
          }
          if (!formatCode) return@withModalProgress

          reporter.text(DevKitWorkspaceModelBundle.message("progress.title.project.analysis"))

          DumbService.getInstance(project).waitForSmartMode()

          reporter.text(DevKitWorkspaceModelBundle.message("progress.title.reformatting.code"))

          for ((i, file) in generatedFiles.withIndex()) {
            reporter.fraction(0.25 + 0.7 * i / generatedFiles.size)
            DumbService.getInstance(project).waitForSmartMode()
            // this has to be invoked outside EDT because the callback scheduleWhenSettingsComputed is invoked on EDT
            file.awaitCodeStyleCalculation()

            writeCommandActionWithGroupId(project, commandName) {  // same groupId chains them
              addCopyright(file, ktClasses)
              LanguageImportStatements.INSTANCE.forFile(file).forEach { it.processFile(file).run() }
              PsiDocumentManager.getInstance(file.project).doPostponedOperationsAndUnblockDocument(file.viewProvider.document!!)
              CodeStyleManager.getInstance(project).reformat(file)
            }
          }
        }
      }
    }
  }

  @RequiresBackgroundThread
  private suspend fun PsiFile.awaitCodeStyleCalculation() {
    val latch = CompletableDeferred<Unit>()
    CodeStyle.getSettings(this) // trigger settings computation
    CodeStyleCachingService.getInstance(project).scheduleWhenSettingsComputed(this) {
      latch.complete(Unit)
    }
    latch.await()
  }

  private fun addCopyright(file: KtFile, ktClasses: HashMap<String, KtClassOrObject>) {
    if (file.name == GENERATED_METADATA_STORAGE_FILE) {
      val someEntitySourceFile = ktClasses.values.firstOrNull { it.name?.contains("Entity") ?: false }?.containingKtFile
      val anySourceFile = someEntitySourceFile ?: ktClasses.values.firstOrNull()?.containingKtFile ?: return
      copyHeaderComment(anySourceFile, file)
      return
    }
    val sourceClassName = getSourceClassNameForGeneratedFile(file)
    val sourceFile = ktClasses[sourceClassName]?.containingKtFile ?: return
    copyHeaderComment(sourceFile, file)
  }

  private data class ApiVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ApiVersion> {
    
    fun compatible(other: ApiVersion): Boolean {
      return major == other.major && minor == other.minor
    }

    override fun toString(): String = "$major.$minor.$patch"
    
    override fun compareTo(other: ApiVersion): Int {
      return when {
        major != other.major -> major - other.major
        minor != other.minor -> minor - other.minor
        else -> patch - other.patch
      }
    }
  }
  
  private fun parseCodegenApi(codegenApiVersion: String): ApiVersion {
    val (major, minor, patch) = codegenApiVersion.split(".", "-").take(3).map { it.toInt() }
    return ApiVersion(major, minor, patch)
  }

  private fun codegenApiVersionsAreCompatible(project: Project, codeGeneratorFromDownloadedJar: CodeGenerator): Boolean {
    val apiVersionInDevkit = getApiVersionFromJSON(CodeGenerator::class.java).let { parseCodegenApi(it) }
    val apiVersionFromDownloadedJar = getApiVersionFromManifest(codeGeneratorFromDownloadedJar::class.java).let { parseCodegenApi(it) }

    if (apiVersionInDevkit.compatible(apiVersionFromDownloadedJar)) {
      val groupId = DevKitWorkspaceModelBundle.message("notification.workspace.compatible.but.different.codegen.api.versions")
      val message = DevKitWorkspaceModelBundle.message("notification.workspace.compatible.but.different.codegen.api.versions.content",
                                                       apiVersionInDevkit,
                                                       apiVersionFromDownloadedJar)
      NotificationGroupManager.getInstance()
        .getNotificationGroup(groupId)
        .createNotification(title = groupId, message, NotificationType.WARNING)
        .notify(project)
      return true
    }

    val message =
      if (apiVersionInDevkit > apiVersionFromDownloadedJar) {
        DevKitWorkspaceModelBundle.message("notification.workspace.incompatible.codegen.api.versions.content.newer",
                                           apiVersionInDevkit,
                                           apiVersionFromDownloadedJar)
      }
      else {
        DevKitWorkspaceModelBundle.message("notification.workspace.incompatible.codegen.api.versions.content.older",
                                           apiVersionInDevkit,
                                           apiVersionFromDownloadedJar)
      }

    val groupId = DevKitWorkspaceModelBundle.message("notification.workspace.incompatible.codegen.api.versions")
    NotificationGroupManager.getInstance()
      .getNotificationGroup(groupId)
      .createNotification(title = groupId, message, NotificationType.ERROR)
      .notify(project)

    return false
  }

  private fun getApiVersionFromJSON(clazz: Class<*>): String {
    return getApiVersionFromJarFile(clazz, CodegenApiVersion.JSON_RELATIVE_PATH) { jsonPath ->
      URI(jsonPath).toURL().openStream().reader().use { reader ->
        val jsonReader = JsonReaderEx(reader.readText())
        val objects = JsonUtil.nextObject(jsonReader)
        objects[CodegenApiVersion.ATTRIBUTE_NAME] as? String
      }
    }
  }

  private fun getApiVersionFromManifest(clazz: Class<*>): String {
    return getApiVersionFromJarFile(clazz, CodegenApiVersion.MANIFEST_RELATIVE_PATH) { manifestPath ->
      URI(manifestPath).toURL().openStream().use {
        val manifest = Manifest(it)
        val attributes = manifest.mainAttributes
        attributes.getValue(CodegenApiVersion.ATTRIBUTE_NAME)
      }
    }
  }

  private fun getApiVersionFromJarFile(clazz: Class<*>, relativePathToFile: String, readApiVersionFromFile: (String) -> String?): String {
    val classPath = "${clazz.name.replace(".", "/")}.class"
    val classAbsolutePath = clazz.getResource("${clazz.simpleName}.class")?.toString() // Absolute path is jar path + class path
                            ?: error("Absolute path for the class $clazz was not found")

    val fileAbsolutePath = classAbsolutePath.replace(classPath, relativePathToFile)

    val apiVersion: String?
    try {
      apiVersion = readApiVersionFromFile(fileAbsolutePath)
    }
    catch (e: IOException) {
      LOG.info("Failed to read codegen-api version from file \"$fileAbsolutePath\": " + e.message)
      return CodegenApiVersion.UNKNOWN_VERSION
    }

    return apiVersion ?: CodegenApiVersion.UNKNOWN_VERSION
  }

  private fun generate(
    codeGenerator: CodeGenerator, objModules: List<CompiledObjModule>,
    explicitApiEnabled: Boolean, isTestModule: Boolean,
  ): List<GenerationResult> {
    val generatorSettings = GeneratorSettings(explicitApiEnabled = explicitApiEnabled, testModeEnabled = isTestModule)
    val entitiesImplementations = objModules.map { codeGenerator.generateEntitiesImplementation(it, generatorSettings) }
      .filter { it.generatedCode.isNotEmpty() || it.problems.isNotEmpty() }
    if (entitiesImplementations.any { it.problems.any { problem -> problem.level == GenerationProblem.Level.ERROR } })
      return entitiesImplementations
    val metadataStorageImplementation = codeGenerator.generateMetadataStoragesImplementation(objModules, generatorSettings)
    return entitiesImplementations + metadataStorageImplementation
  }

  private fun removeGeneratedCode(genFolder: VirtualFile) {
    //remove generated files, e.g. MetadataStorageImpl.kt
    removeFiles(genFolder) {
      it.isGeneratedFile
    }

    //remove empty packages
    removeFiles(genFolder) { vfs ->
      vfs.isDirectory && vfs != genFolder && VfsUtil.collectChildrenRecursively(vfs).all { it.isDirectory }
    }
  }

  private fun removeFiles(baseFolder: VirtualFile, filter: (VirtualFile) -> Boolean) {
    val filesToRemove = arrayListOf<VirtualFile>()

    VfsUtilCore.processFilesRecursively(baseFolder) {
      if (filter.invoke(it)) {
        filesToRemove.add(it)
      }
      return@processFilesRecursively true
    }

    filesToRemove.forEach { it.delete(CodeWriter) }
  }

  private fun addGeneratedObjModuleFile(
    code: ObjModuleFileGeneratedCode, generatedFiles: MutableList<KtFile>,
    project: Project, sourceFolder: VirtualFile, genFolder: VirtualFile,
    sourceFilePerObjModule: Map<String, VirtualFile>,
    importsByFile: MutableMap<KtFile, Imports>, psiFactory: KtPsiFactory,
  ) {
    val packageFqnName = code.objModuleName

    val sourceFile = sourceFilePerObjModule[packageFqnName]!!
    val targetDirectory = getPsiDirectory(project, genFolder, sourceFolder, sourceFile)

    val implPackageFqnName = "$packageFqnName.impl"
    val implImports = Imports(implPackageFqnName)
    val implFile = psiFactory.createFile("${code.fileName}.kt", implImports.findAndRemoveFqns(code.generatedCode))

    targetDirectory.findFile(implFile.name)?.delete()

    val addedFile = targetDirectory.add(implFile) as KtFile
    generatedFiles.add(addedFile)
    importsByFile[addedFile] = implImports
  }

  private fun addGeneratedObjClassFile(
    code: ObjClassGeneratedCode, generatedFiles: MutableList<KtFile>, project: Project, sourceFolder: VirtualFile, genFolder: VirtualFile,
    ktClasses: Map<String, KtClassOrObject>, importsByFile: MutableMap<KtFile, Imports>, psiFactory: KtPsiFactory,
  ) {
    if (code.target.name in SKIPPED_TYPES) return

    val target = code.target
    val apiInterfaceName = "${target.module.name}.${target.name}"
    val apiClass = ktClasses[apiInterfaceName]
    if (apiClass == null) {
      LOG.warn("Class $apiInterfaceName was not found")
      return
    }
    val apiPackageFqName = apiClass.containingKtFile.packageFqName
    val sourceFile = apiClass.containingFile.virtualFile
    val targetDirectory = getPsiDirectory(project, genFolder, sourceFolder, sourceFile)
    run {
      val apiPackageFqnName = apiPackageFqName.asString()
      val generatedApiImports = Imports(apiPackageFqnName)
      apiClass.containingKtFile.importDirectives.mapNotNull { it.importPath }.forEach { import ->
        generatedApiImports.add(import.pathStr)
      }
      val psiFactory = KtPsiFactory(apiClass.project)
      val topLevelCode = code.topLevelCode ?: ""
      val filename = "${code.target.name}$GENERATED_MODIFICATIONS_SUFFIX"
      val generatedModificationsFile = psiFactory.createFile(filename, generatedApiImports.findAndRemoveFqns(topLevelCode))
      generatedModificationsFile.packageFqName = apiPackageFqName

      val declarations = generatedModificationsFile.declarations
      for (declaration in declarations) {
        if (declaration.firstChild.text.contains("Deprecated")) {
          declaration.delete()
          continue
        }
      }

      val visibility = apiClass.visibilityModifierType().takeIf { !apiClass.isPublic }
      if (visibility != null) {
        generatedModificationsFile.declarations.forEach { it.addModifier(visibility) }
      }

      val apiClassFileAnnotationsNoJvmName = apiClass.containingKtFile.annotationEntries.filter { it.shortName?.asString() != "JvmName" }
      val generatedModificationsFileAnnotations = generatedModificationsFile.fileAnnotationList
                                                  ?: error("Generated EntityModifications file should have at least JvmName annotation, but it is missing for ${generatedModificationsFile.name}")
      apiClassFileAnnotationsNoJvmName.forEach { annotation ->
        generatedModificationsFileAnnotations.add(annotation)
      }

      val apiTargetDirectory = targetDirectory.parent!!
      apiTargetDirectory.findFile(generatedModificationsFile.name)?.delete()
      //todo remove other old generated files
      val addedFile = apiTargetDirectory.add(generatedModificationsFile) as KtFile
      generatedFiles.add(addedFile)
      importsByFile[addedFile] = generatedApiImports
    }
    val implementationClassText = code.implementationClass
    if (implementationClassText != null) {
      val implPackageFqnName = "${apiPackageFqName.asString()}.impl"
      val implImports = Imports(implPackageFqnName)
      val implFile =
        psiFactory.createFile("${code.target.name}$GENERATED_IMPL_SUFFIX", implImports.findAndRemoveFqns(implementationClassText))
      apiClass.containingKtFile.importDirectives.mapNotNull { it.importPath }.forEach { import ->
        implImports.add(import.pathStr)
      }
      targetDirectory.findFile(implFile.name)?.delete() //todo remove other old generated files
      val addedFile = targetDirectory.add(implFile) as KtFile
      generatedFiles.add(addedFile)
      importsByFile[addedFile] = implImports
    }
  }

  private fun getPsiDirectory(project: Project, genFolder: VirtualFile, sourceFolder: VirtualFile, sourceFile: VirtualFile): PsiDirectory {
    val relativePath = VfsUtil.getRelativePath(sourceFile.parent, sourceFolder, '/')
    // We store entities' implementation in impl package
    val packageFolder = VfsUtil.createDirectoryIfMissing(genFolder, "$relativePath/impl")
    return PsiManager.getInstance(project).findDirectory(packageFolder)!!
  }

  private fun copyHeaderComment(apiFile: KtFile, implFile: KtFile) {
    val firstNonComment = apiFile.fileAnnotationList?.node ?: apiFile.packageDirective?.node ?: return
    val implFileNode = implFile.node
    var nodeToCopy = firstNonComment.treePrev
    var anchorBefore = implFileNode.firstChildNode
    while (nodeToCopy != null) {
      val copied = nodeToCopy.copyElement()
      implFileNode.addChild(copied, anchorBefore)
      anchorBefore = copied
      nodeToCopy = nodeToCopy.treePrev
    }
  }

  private fun addImports(file: KtFile, imports: Imports) {
    val psiFactory = KtPsiFactory(file.project)
    val importList = file.importList ?: error("no imports in ${file.name}")
    importList.imports.asSequence().mapNotNull { it.importPath?.pathStr }.forEach { imports.add(it) }
    if (importList.children.isNotEmpty()) {
      importList.deleteChildRange(importList.firstChild, importList.lastChild)
    }
    imports.imports.sorted().forEach {
      val importDirective = psiFactory.createImportDirective(ImportPath.fromString(it))
      importList.add(importDirective)
      importList.add(psiFactory.createNewLine())
    }
    if (imports.isNotEmpty()) {
      importList.lastChild.delete()
    }
  }

  private fun getSourceClassNameForGeneratedFile(file: KtFile): String? {
    val packageName = file.packageFqName.asString()
    if (file.name.endsWith(GENERATED_IMPL_SUFFIX)) {
      // packageName includes "impl"
      return "${packageName.dropLast(4)}${file.name.dropLast(GENERATED_IMPL_SUFFIX.length)}"
    }
    if (file.name.endsWith(GENERATED_MODIFICATIONS_SUFFIX)) {
      return "$packageName.${file.name.dropLast(GENERATED_MODIFICATIONS_SUFFIX.length)}"
    }
    return null
  }

  private const val GENERATED_IMPL_SUFFIX = "Impl.kt"

  private const val GENERATED_MODIFICATIONS_SUFFIX = "Modifications.kt"

  private const val GENERATED_METADATA_STORAGE_FILE = "MetadataStorageImpl.kt"

  private val GENERATED_FILES = setOf(GENERATED_METADATA_STORAGE_FILE)

  private val VirtualFile.isGeneratedFile: Boolean
    get() = extension == "kt" && GENERATED_FILES.contains(name)

  private object CodegenApiVersion {
    const val JSON_RELATIVE_PATH = "codegen-api-metadata.json"
    const val MANIFEST_RELATIVE_PATH = "META-INF/MANIFEST.MF"

    const val ATTRIBUTE_NAME = "Codegen-Api-Version"

    const val UNKNOWN_VERSION = "unknown version"
  }
}

private suspend fun <T> writeCommandActionWithGroupId(
  project: Project,
  @NlsContexts.Command commandName: String,
  action: () -> T,
): T {
  return withContext(Dispatchers.EDT) {
    writeIntentReadAction {
      WriteCommandAction.writeCommandAction(project)
        .withName(commandName)
        .withGroupId(commandName)
        .compute<T, Throwable>(action)
    }
  }
}