// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.validation

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.ui.UIBundle
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path

val CHECK_NON_EMPTY = validationErrorIf<String>(UIBundle.message("kotlin.dsl.validation.missing.value")) { it.isEmpty() }

val CHECK_NO_WHITESPACES = validationErrorIf<String>(UIBundle.message("kotlin.dsl.validation.no.whitespaces")) { ' ' in it }

private val reservedWordsPattern = "(^|[ .])(con|prn|aux|nul|com\\d|lpt\\d)($|[ .])".toRegex(RegexOption.IGNORE_CASE)
val CHECK_NO_RESERVED_WORDS = validationErrorIf<String>(UIBundle.message("kotlin.dsl.validation.no.reserved.words")) {
  reservedWordsPattern.find(it) != null
}

private val namePattern = "[a-zA-Z\\d\\s_.-]*".toRegex()
private val firstSymbolNamePattern = "[a-zA-Z_].*".toRegex()
val CHECK_NAME_FORMAT = validationErrorIf<String>(UIBundle.message("kotlin.dsl.validation.name.allowed.symbols")) {
  !namePattern.matches(it)
} and validationErrorIf<String>(UIBundle.message("kotlin.dsl.validation.name.leading.symbols")) {
  !firstSymbolNamePattern.matches(it)
}

val CHECK_NON_EMPTY_DIRECTORY = validationFileErrorFor { file ->
  val children by lazy { file.list() }
  if (file.exists() && children != null && children.isNotEmpty()) {
    UIBundle.message("label.project.wizard.new.project.directory.not.empty.warning", file.name)
  }
  else null
}.asWarning().withOKEnabled()

val CHECK_DIRECTORY = validationErrorFor<String> { text ->
  runCatching { Path.of(text).toFile() }
    .mapCatching { file ->
      when {
        !file.exists() -> null
        !file.canWrite() -> UIBundle.message("label.project.wizard.new.project.directory.not.writable.error", file.name)
        !file.isDirectory -> UIBundle.message("label.project.wizard.new.project.file.not.directory.error", file.name)
        else -> null
      }
    }.getOrElse { exception ->
      when (exception) {
        is InvalidPathException -> exception.message
        is IOException -> exception.message
        else -> throw exception
      }
    }
}

private val CHECK_GROUP_ID_FORMAT = validationErrorFor<String> { text ->
  if (text.startsWith('.') || text.endsWith('.')) {
    UIBundle.message("kotlin.dsl.validation.groupId.leading.trailing.dot")
  }
  else if (".." in text) {
    UIBundle.message("kotlin.dsl.validation.groupId.double.dot")
  }
  else {
    text.split(".")
      .find { !firstSymbolNamePattern.matches(it) }
      ?.let { UIBundle.message("kotlin.dsl.validation.groupId.part.allowed.symbols", it) }
  }
}

val CHECK_GROUP_ID = CHECK_NO_WHITESPACES and CHECK_NAME_FORMAT and CHECK_GROUP_ID_FORMAT and CHECK_NO_RESERVED_WORDS

val CHECK_ARTIFACT_ID = CHECK_NO_WHITESPACES and CHECK_NAME_FORMAT and CHECK_NO_RESERVED_WORDS

private fun Project.getModules() = ModuleManager.getInstance(this).modules

val CHECK_FREE_MODULE_NAME = validationErrorFor<Project?, String> { project, name ->
  project?.getModules()
    ?.find { it.name == name }
    ?.let { UIBundle.message("label.project.wizard.new.module.name.exists.error", it.name) }
}

val CHECK_FREE_MODULE_PATH = validationPathErrorFor<Project?> { project, path ->
  project?.getModules()
    ?.find { m -> m.rootManager.contentRoots.map { it.toNioPath() }.any { it == path } }
    ?.let { UIBundle.message("label.project.wizard.new.module.directory.already.taken.error", it.name) }
}

val CHECK_FREE_PROJECT_PATH = validationPathErrorFor { path ->
  val project = ProjectUtil.findProject(path)
  if (project != null) {
    UIBundle.message("label.project.wizard.new.project.directory.already.taken.error", project.name)
  }
  else {
    null
  }
}

val CHECK_MODULE_NAME = CHECK_NAME_FORMAT and CHECK_FREE_MODULE_NAME and CHECK_NO_RESERVED_WORDS

val CHECK_MODULE_PATH = CHECK_DIRECTORY and CHECK_NON_EMPTY_DIRECTORY and CHECK_FREE_MODULE_PATH

val CHECK_PROJECT_PATH = CHECK_MODULE_PATH and CHECK_FREE_PROJECT_PATH
