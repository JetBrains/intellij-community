// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.impl

import com.intellij.conversion.*
import com.intellij.conversion.impl.ConversionContextImpl
import com.intellij.conversion.impl.ConversionRunner
import com.intellij.conversion.impl.ProjectConversionUtil
import com.intellij.conversion.impl.ui.ConvertProjectDialog
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.project.ProjectStoreOwner
import it.unimi.dsi.fastutil.objects.Object2LongMaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier

internal class ConversionServiceImpl : ConversionService() {
  override fun convertSilently(projectPath: Path, conversionListener: ConversionListener): ConversionResult {
    try {
      val context = ConversionContextImpl(projectPath)
      val runners = isConversionNeeded(context)
      if (runners.isEmpty()) {
        return ConversionResultImpl.CONVERSION_NOT_NEEDED
      }

      conversionListener.conversionNeeded()
      val affectedFiles = HashSet<Path>()
      for (runner in runners) {
        runner.collectAffectedFiles(affectedFiles)
      }
      val readOnlyFiles = ConversionRunner.getReadOnlyFiles(affectedFiles)
      if (!readOnlyFiles.isEmpty()) {
        conversionListener.cannotWriteToFiles(readOnlyFiles)
        return ConversionResultImpl.ERROR_OCCURRED
      }

      val backupDir = ProjectConversionUtil.backupFiles(affectedFiles, context.projectBaseDir)
      for (runner in runners) {
        if (runner.isConversionNeeded) {
          runner.preProcess()
          runner.process()
          runner.postProcess()
        }
      }
      context.saveFiles(affectedFiles)
      conversionListener.successfullyConverted(backupDir)
      context.saveConversionResult()
      return ConversionResultImpl(runners)
    }
    catch (e: CannotConvertException) {
      conversionListener.error(e.message!!)
    }
    catch (e: IOException) {
      conversionListener.error(e.message!!)
    }
    return ConversionResultImpl.ERROR_OCCURRED
  }

  @Throws(CannotConvertException::class)
  override suspend fun convert(projectPath: Path): ConversionResult {
    if (ApplicationManager.getApplication().isHeadlessEnvironment ||
        !ConverterProvider.EP_NAME.hasAnyExtensions() ||
        !Files.exists(projectPath)) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED
    }

    val context = ConversionContextImpl(projectPath)
    val converters = blockingContext {
      isConversionNeeded(context)
    }
    if (converters.isEmpty()) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED
    }

    val result: ConversionResultImpl
    if (ApplicationManagerEx.isInIntegrationTest()) {
      result = ConversionResultImpl(converters)
    }
    else {
      result = withContext(Dispatchers.EDT) {
        val dialog = ConvertProjectDialog(context, converters)
        dialog.show()
        if (dialog.isConverted) {
          ConversionResultImpl(converters)
        }
        else {
          ConversionResultImpl.CONVERSION_CANCELED
        }
      }
    }

    try {
      context.saveConversionResult()
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    return result
  }

  override fun saveConversionResult(projectPath: Path) {
    try {
      ConversionContextImpl(projectPath).saveConversionResult()
    }
    catch (e: IOException) {
      LOG.error(e)
    }
    catch (e: CannotConvertException) {
      LOG.info(e)
    }
  }

  override fun convertModule(project: Project, moduleFile: Path): ConversionResult {
    val context = ConversionContextImpl((project as ProjectStoreOwner).componentStore.projectFilePath)
    val runners = ArrayList<ConversionRunner>()
    try {
      val point = ConverterProvider.EP_NAME.point as ExtensionPointImpl<ConverterProvider>
      point.processIdentifiableImplementations { supplier, id ->
        val provider = supplier.get()
        if (provider != null) {
          val runner = ConversionRunner(getProviderId(supplier, id), provider, context)
          if (runner.isModuleConversionNeeded(moduleFile)) {
            runners.add(runner)
          }
        }
      }
    }
    catch (e: CannotConvertException) {
      LOG.info(e)
    }
    if (runners.isEmpty()) {
      return ConversionResultImpl.CONVERSION_NOT_NEEDED
    }
    val answer = Messages.showYesNoDialog(project,
                                          IdeBundle.message("message.module.file.has.an.older.format.do.you.want.to.convert.it"),
                                          IdeBundle.message("dialog.title.convert.module"), Messages.getQuestionIcon())
    if (answer != Messages.YES) {
      return ConversionResultImpl.CONVERSION_CANCELED
    }
    if (!Files.isWritable(moduleFile)) {
      Messages.showErrorDialog(project, IdeBundle.message("error.message.cannot.modify.file.0", moduleFile.toAbsolutePath().toString()),
                               IdeBundle.message("dialog.title.convert.module"))
      return ConversionResultImpl.ERROR_OCCURRED
    }

    try {
      val backupFile = ProjectConversionUtil.backupFile(moduleFile)
      for (runner in runners) {
        runner.convertModule(moduleFile)
      }
      context.saveFiles(listOf(moduleFile))
      Messages.showInfoMessage(project, IdeBundle.message("message.your.module.was.successfully.converted.br.old.version.was.saved.to.0",
                                                          backupFile.absolutePath),
                               IdeBundle.message("dialog.title.convert.module"))
      return ConversionResultImpl(runners)
    }
    catch (e: CannotConvertException) {
      LOG.info(e)
      Messages.showErrorDialog(IdeBundle.message("error.cannot.load.project", e.message),
                               VcsBundle.message("dialog.title.cannot.convert.module"))
      return ConversionResultImpl.ERROR_OCCURRED
    }
    catch (e: IOException) {
      LOG.info(e)
      return ConversionResultImpl.ERROR_OCCURRED
    }
  }
}

private val LOG = logger<ConversionServiceImpl>()

private fun isConversionNeeded(context: ConversionContextImpl): List<ConversionRunner> {
  try {
    val oldMap = context.projectFileTimestamps
    var changed = false
    if (oldMap.isEmpty()) {
      LOG.debug("conversion will be performed because no information about project files")
    }
    else {
      val newMap = context.allProjectFiles
      LOG.debug("Checking project files")
      val iterator = Object2LongMaps.fastIterator(newMap)
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val path = entry.key
        val oldValue = oldMap.getLong(path)
        val newValue = entry.longValue
        if (newValue != oldValue) {
          LOG.info("conversion will be performed because at least $path is changed (oldLastModified=$oldValue, newLastModified=$newValue)")
          changed = true
          break
        }
      }
    }
    val performedConversionIds: Set<String>
    if (changed) {
      performedConversionIds = emptySet()
    }
    else {
      performedConversionIds = context.appliedConverters
      if (LOG.isDebugEnabled) {
        LOG.debug("Project files are up to date. Applied converters: $performedConversionIds")
      }
    }

    val runners = ArrayList<ConversionRunner>()
    val point = ConverterProvider.EP_NAME.point as ExtensionPointImpl<ConverterProvider>
    point.processIdentifiableImplementations { supplier, id ->
      val providerId = getProviderId(supplier, id)
      if (!performedConversionIds.contains(providerId)) {
        val provider = supplier.get()
        if (provider != null) {
          val runner = ConversionRunner(providerId, provider, context)
          if (runner.isConversionNeeded) {
            runners.add(runner)
          }
        }
      }
    }
    return runners
  }
  catch (e: CannotConvertException) {
    LOG.info("Cannot check whether conversion of project files is needed or not, conversion won't be performed", e)
    return emptyList()
  }
}

private fun getProviderId(supplier: Supplier<out ConverterProvider?>, id: String?): String {
  return id ?: supplier.get()!!.deprecatedId
}
