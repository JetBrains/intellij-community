// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.java.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.DirectoryContentBuilder
import com.intellij.util.io.java.AccessModifier
import com.intellij.util.io.java.ClassFileBuilder
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import kotlin.reflect.KClass

@Suppress("unused") // instantiated reflectively
class ClassFileBuilderImpl(private val name: String) : ClassFileBuilder() {

  private val writer = ClassWriter(ClassWriter.COMPUTE_FRAMES)

  override fun field(name: String, type: String, access: AccessModifier) {
    addField(name, "L" + toJvmName(type) + ";", access)
  }

  override fun field(name: String, type: KClass<*>, access: AccessModifier) {
    addField(name, Type.getDescriptor(type.java), access)
  }

  private fun addField(name: String, typeDescriptor: String, access: AccessModifier) {
    writer.visitField(access.toAsmCode(), name, typeDescriptor, null, null).visitEnd()
  }

  override fun generate(targetRoot: DirectoryContentBuilder) {
    writer.visit(javaVersion.toAsmCode(), access.toAsmCode(), toJvmName(name), null,
                 superclass.replace('.', '/'),
                 interfaces.map(::toJvmName).toTypedArray())
    writer.visitEnd()

    targetRoot.directories(StringUtil.getPackageName(name).replace('.', '/')) {
      file("${StringUtil.getShortName(name)}.class", writer.toByteArray())
    }
  }

  private fun DirectoryContentBuilder.directories(relativePath: String, content: DirectoryContentBuilder.() -> Unit) {
    if (relativePath.isEmpty()) {
      content()
    }
    else {
      directories(PathUtil.getParentPath(relativePath)) {
        dir(PathUtil.getFileName(relativePath)) {
          content()
        }
      }
    }
  }
}

private fun toJvmName(className: String) = className.replace('.', '/')

private fun LanguageLevel.toAsmCode() = when (this) {
  LanguageLevel.JDK_1_3 -> Opcodes.V1_3
  LanguageLevel.JDK_1_4 -> Opcodes.V1_4
  LanguageLevel.JDK_1_5 -> Opcodes.V1_5
  LanguageLevel.JDK_1_6 -> Opcodes.V1_6
  LanguageLevel.JDK_1_7 -> Opcodes.V1_7
  LanguageLevel.JDK_1_8 -> Opcodes.V1_8
  LanguageLevel.JDK_1_9 -> Opcodes.V9
  else -> throw UnsupportedOperationException("${this} isn't supported yet")
}

private fun AccessModifier.toAsmCode() = when (this) {
  AccessModifier.PROTECTED -> Opcodes.ACC_PROTECTED
  AccessModifier.PRIVATE -> Opcodes.ACC_PRIVATE
  AccessModifier.PUBLIC -> Opcodes.ACC_PUBLIC
  AccessModifier.PACKAGE_LOCAL -> 0
}