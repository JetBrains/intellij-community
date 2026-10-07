// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml.codeInsight.intentions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import java.io.IOException
import java.util.TreeSet
import java.util.function.Consumer

private val LOG = logger<JavaFxScriptEngineService>()

private const val SCRIPT_ENGINE_FACTORY_SERVICE = "META-INF/services/javax.script.ScriptEngineFactory"

/** The maximum number of superclasses to follow when a factory inherits `getNames()`. */
private const val MAX_SUPERCLASS_DEPTH = 5

/**
 * Finds the script engines of the project libraries.
 * The service reads the `ServiceLoader` provider files and the bytecode of the provider classes.
 */
@Service(Service.Level.PROJECT)
class JavaFxScriptEngineService(private val project: Project, private val coroutineScope: CoroutineScope) {
  @RequiresEdt
  fun findEngineNames(onFound: Consumer<Set<String>>): Job {
    val modalityState = ModalityState.defaultModalityState()
    return coroutineScope.launch(modalityState.asContextElement()) {
      val engineNames = findEngineNames()
      withContext(Dispatchers.EDT) {
        onFound.accept(engineNames)
      }
    }
  }

  suspend fun findEngineNames(): Set<String> {
    val roots = readAction {
      OrderEnumerator.orderEntries(project).recursively().librariesOnly().runtimeOnly().classes().roots
    }
    return withContext(Dispatchers.IO) {
      val engineNames = TreeSet<String>()
      for (root in roots) {
        ensureActive()
        val serviceFile = root.findFileByRelativePath(SCRIPT_ENGINE_FACTORY_SERVICE)
        if (serviceFile == null || serviceFile.isDirectory) continue
        val factoryClassNames = try {
          parseProviderClassNames(VfsUtilCore.loadText(serviceFile))
        }
        catch (e: IOException) {
          LOG.info(e)
          continue
        }
        for (factoryClassName in factoryClassNames) {
          try {
            // the first name of getNames() is the main one, the others are aliases
            readNames(roots, factoryClassName.replace('.', '/'), 0).firstOrNull()?.let { engineNames.add(it) }
          }
          catch (e: IOException) {
            LOG.info(e)
          }
          catch (e: IllegalArgumentException) {
            LOG.info("Malformed class file of $factoryClassName", e)
          }
          catch (e: IndexOutOfBoundsException) {
            LOG.info("Malformed class file of $factoryClassName", e)
          }
        }
      }
      engineNames
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): JavaFxScriptEngineService = project.service()
  }
}

private fun parseProviderClassNames(text: String): List<String> {
  return StringUtil.splitByLines(text)
    .map { line -> line.substringBefore('#').trim() }
    .filter { it.isNotEmpty() }
}

private fun readNames(roots: Array<VirtualFile>, internalClassName: String, depth: Int): List<String> {
  if (depth > MAX_SUPERCLASS_DEPTH) return emptyList()
  val classFile = roots.firstNotNullOfOrNull { it.findFileByRelativePath("$internalClassName.class") } ?: return emptyList()
  val reader = ClassReader(classFile.contentsToByteArray())
  val collector = NamesCollector()
  reader.accept(collector, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
  if (!collector.hasGetNames) {
    val superName = reader.superName ?: return emptyList()
    return readNames(roots, superName, depth + 1)
  }
  return collector.directNames.ifEmpty { collector.fieldNames[collector.returnedField].orEmpty() }
}

private class NamesCollector : ClassVisitor(Opcodes.API_VERSION) {
  var hasGetNames = false
  val directNames = mutableListOf<String>()
  var returnedField: String? = null
  val fieldNames = mutableMapOf<String, List<String>>()

  override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
    return when (name) {
      "getNames" -> if (descriptor != "()Ljava/util/List;") null else {
        hasGetNames = true
        object : MethodVisitor(api) {
          override fun visitLdcInsn(value: Any?) {
            if (value is String) directNames.add(value)
          }

          override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
            if (opcode == Opcodes.GETFIELD || opcode == Opcodes.GETSTATIC) returnedField = name
          }
        }
      }
      "<init>", "<clinit>" -> object : MethodVisitor(api) {
        private val pending = mutableListOf<String>()

        override fun visitLdcInsn(value: Any?) {
          if (value is String) pending.add(value)
        }

        override fun visitFieldInsn(opcode: Int, owner: String, name: String, descriptor: String) {
          if (opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC) {
            if (pending.isNotEmpty()) fieldNames[name] = pending.toList()
            pending.clear()
          }
        }
      }
      else -> null
    }
  }
}
