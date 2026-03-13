// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.execution.configurations.ParametersList
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.ModuleManagerEx
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.systemOs
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.ijent.community.buildConstants.IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS
import com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.util.JavaModuleOptions
import org.jetbrains.idea.devkit.DevKitBundle
import java.lang.ClassLoader.getSystemClassLoader
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.io.path.exists

internal object DevKitPatcherHelper {
  private val LOG = logger<DevKitPatcherHelper>()
  private val LOADER_VALID = Key.create<Boolean?>("LOADER_VALID_9")

  const val SYSTEM_CL_PROPERTY: String = "java.system.class.loader"

  @JvmStatic
  fun appendAddOpensWhenNeeded(project: Project, jdk: Sdk, vm: ParametersList) {
    val sdkVersion: JavaSdkVersion? = (jdk.getSdkType() as? JavaSdk)?.getVersion(jdk)
    if (sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_25)) {
      vm.add("--enable-native-access=ALL-UNNAMED")
    }
    if (sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_17)) {
      val scope = ProjectScope.getContentScope(project)
      val files = ReadAction.computeBlocking<Collection<VirtualFile>, RuntimeException> {
        FilenameIndex.getVirtualFilesByName("OpenedPackages.txt", scope)
      }
      if (files.size > 1) {
        val list = files.joinToString(separator = "\n", transform = { it.presentableUrl })
        val message = DevKitBundle.message("notification.message.duplicate.packages.file", list)
        Notification("DevKit Errors", message, NotificationType.ERROR).notify(project)
      }
      else if (!files.isEmpty()) {
        val file = files.first()
        val projectFilePath = project.getProjectFilePath() ?: throw IllegalStateException("Run configurations should not be invoked on the default project")
        val eelApi = Path.of(projectFilePath).getEelDescriptor().toEelApiBlocking()
        val targetOs = eelApi.systemOs()
        try {
          file.getInputStream().use { stream ->
            JavaModuleOptions.readOptions(stream, targetOs).forEach(Consumer { parameter: String? -> vm.add(parameter) })
          }
        }
        catch (e: ProcessCanceledException) {
          throw e //unreachable
        }
        catch (e: Throwable) {
          LOG.error("Failed to load --add-opens list from 'OpenedPackages.txt'", e)
        }
      }
    }
  }

  @JvmStatic
  fun loaderValid(project: Project, module: Module?, qualifiedName: String): Boolean {
    val holder: UserDataHolder = module ?: project
    var result = holder.getUserData(LOADER_VALID)
    if (result == null) {
      result = ReadAction.computeBlocking<Boolean?, RuntimeException> {
        getInstance(project).computeWithAlternativeResolveEnabled<Boolean?, RuntimeException?> {
          val scope = if (module != null) GlobalSearchScope.moduleRuntimeScope(module, true) else GlobalSearchScope.allScope(project)
          JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope) != null
        }
      }
      holder.putUserData(LOADER_VALID, result)
    }
    return result
  }

  @JvmStatic
  fun enableIjentDefaultFsProvider(project: Project, vmParameters: ParametersList) {
    // Enable the ijent file system only when the new default FS provider class is available.
    // It is required to let actual DevKit plugins work with branches without the FS provider class, like 241.
    if (loaderValid(project, null, IJENT_REQUIRED_DEFAULT_NIO_FS_PROVIDER_CLASS)) {
      val isIjentWslFsEnabled = isIjentWslFsEnabledByDefaultForProduct_Reflective(
        project = project,
        platformPrefix = vmParameters.getPropertyValue("idea.platform.prefix"),
      )

      // This option doesn't exist anymore, but it used to exist in old versions.
      vmParameters.add("-Dwsl.use.remote.agent.for.nio.filesystem=$isIjentWslFsEnabled")

      vmParameters.addAll(getMultiRoutingFileSystemVmOptions_Reflective(project))

      val outputRoot = getOutputByModule(project, IJENT_BOOT_CLASSPATH_MODULE, "ijent boot classpath addition")
      if (outputRoot != null) {
        vmParameters.add("-Xbootclasspath/a:$outputRoot")
      }
    }
  }

  /**
   * A direct call of [com.intellij.platform.ijent.community.buildConstants.isMultiRoutingFileSystemEnabledForProduct] invokes
   * the function which is bundled with the DevKit plugin.
   * In contrast, the result of this function corresponds to what is written in the source code at the current revision.
   */
  @Suppress("FunctionName")
  private fun isIjentWslFsEnabledByDefaultForProduct_Reflective(project: Project, platformPrefix: String?): Boolean {
    try {
      val constantsClass = getIjentBuildScriptsConstantsClass_Reflective(project) ?: return false
      val method = try {
        constantsClass.getDeclaredMethod("isMultiRoutingFileSystemEnabledForProduct", String::class.java)
      }
      catch (_: NoSuchMethodException) {
        @Suppress("SpellCheckingInspection")
        constantsClass.getDeclaredMethod("isIjentWslFsEnabledByDefaultForProduct", String::class.java)
      }
      return method.invoke(null, platformPrefix) as Boolean
    }
    catch (e: Throwable) {
      when (e) {
        is ClassNotFoundException, is NoSuchMethodException, is IllegalAccessException, is java.lang.reflect.InvocationTargetException -> {
          @Suppress("SpellCheckingInspection")
          logger<DevKitApplicationPatcher>().warn(
            "Failed to reflectively load IjentWslFsEnabledByDefaultForProduct from built classes." +
            " Maybe the file didn't exist in this revision, so the ijent WSL FS was disabled.",
            e,
          )
          return false
        }
        else -> throw e
      }
    }
  }

  @Suppress("FunctionName")
  private fun getIjentBuildScriptsConstantsClass_Reflective(project: Project): Class<*>? {
    val buildConstantsClassPath = getOutputByModule(
      project = project,
      moduleName = "intellij.platform.ijent.community.buildConstants",
      requestMoniker = "ijent build scripts constants class loading",
    ) ?: return null

    val kotlinStdlibClassPath = run {
      val systemClassLoader = getSystemClassLoader()
      val kotlinCollectionsClassUri = systemClassLoader.getResource("kotlin/collections/CollectionsKt.class")!!.toURI()

      if (kotlinCollectionsClassUri.scheme != "jar") {
        logger<DevKitApplicationPatcher>().warn("Kotlin stdlib is not in a JAR: $kotlinCollectionsClassUri")
        return null
      }
      val osPath = kotlinCollectionsClassUri.schemeSpecificPart
        .substringBefore(".jar!")
        .plus(".jar")
        .removePrefix(if (SystemInfo.isWindows) "file:/" else "file:")

      Path.of(osPath).toUri().toURL()
    }

    val tmpClassLoader = URLClassLoader(arrayOf(buildConstantsClassPath.toUri().toURL(), kotlinStdlibClassPath), null)
    return tmpClassLoader.loadClass("com.intellij.platform.ijent.community.buildConstants.IjentBuildScriptsConstantsKt")
  }

  private fun getOutputByModule(project: Project, moduleName: String, requestMoniker: String): Path? {
    val module = ModuleManagerEx.getInstanceEx(project).findModuleByName(moduleName)
    if (module == null) {
      LOG.warn("Module $moduleName not found in project ${project.basePath}, skipping $requestMoniker")
      return null
    }

    val compilerModuleExtension = CompilerModuleExtension.getInstance(module)
    if (compilerModuleExtension == null) {
      LOG.warn("CompilerModuleExtension not found for module ${module.name}, skipping $requestMoniker")
      return null
    }

    // For JPS compilation returns a directory
    // For Bazel delegation returns a jar
    val roots = compilerModuleExtension.getOutputRoots(false)
    if (roots.isEmpty()) {
      LOG.warn("No output roots found for module ${module.name}, skipping $requestMoniker")
      return null
    }

    if (roots.size > 1) {
      LOG.warn("Multiple output roots found for module ${module.name}, skipping $requestMoniker: ${roots.map { it.path }.sorted()}")
      return null
    }

    val outputRoot = roots.single().toNioPath()
    if (!outputRoot.exists()) {
      LOG.warn("Output root for module ${module.name} does not exist: $outputRoot, skipping $requestMoniker")
      return null
    }

    LOG.info("Using output $outputRoot for $requestMoniker")

    return outputRoot
  }

  /**
   * A direct call of [com.intellij.platform.ijent.community.buildConstants.MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS] gets
   * values which is bundled with the DevKit plugin.
   * In contrast, the result of this function corresponds to what is written in the source code at the current revision.
   */
  @Suppress("FunctionName")
  private fun getMultiRoutingFileSystemVmOptions_Reflective(project: Project): List<String> {
    try {
      val constantsClass = getIjentBuildScriptsConstantsClass_Reflective(project) ?: return MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
      val field = constantsClass.getDeclaredField("MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS")
      field.trySetAccessible()
      @Suppress("UNCHECKED_CAST")
      return field.get(constantsClass) as List<String>
    }
    catch (err: Throwable) {
      when (err) {
        is ClassNotFoundException, is NoSuchMethodException, is IllegalAccessException, is java.lang.reflect.InvocationTargetException -> {
          logger<DevKitApplicationPatcher>().warn(
            "Failed to reflectively load MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS from built classes." +
            " Options from DevKit plugin loaded class will be used.",
            err,
          )
          return MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS
        }
        else -> throw err
      }
    }
  }
}
