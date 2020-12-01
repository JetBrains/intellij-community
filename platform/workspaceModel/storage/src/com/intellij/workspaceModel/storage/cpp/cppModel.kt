// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("KDocUnresolvedReference")//until KT-43706 is fixed

package com.intellij.workspaceModel.storage.cpp

import com.intellij.workspaceModel.storage.java.FilePointer
import java.io.File


/**
 * The approximation of the existing C++ model:
 *
 * @see com.jetbrains.cidr.lang.workspace.OCWorkspace
 * @see com.jetbrains.cidr.lang.workspace.OCResolveConfiguration
 * @see com.jetbrains.cidr.lang.workspace.OCCompilerSettings
 */


interface CppConfiguration {
  val name: String

  // source roots in C++ world are mostly files, not folders
  val sourceRoots: List<CppSourceRoot>

  val defaultCompilerSettings: CppCompilerSettings
  val languageCompilerSettings: Map<CppLanguage, CppCompilerSettings>
}

enum class CppLanguage { C, CPP, OC, OCPP }

interface CppSourceRoot {
  val file: FilePointer
  val generated: Boolean
  val language: CppLanguage? /* normally the language is determined by file extension, but it can be customized */

  val compilerSettings: CppCompilerSettings

  val output: String?
}

enum class CppCompilerKind { CLANG, GCC, MSVC }

interface CppCompilerSettings {
  val compilerKind: CppCompilerKind?
  val compilerExecutable: File?
  val compilerWorkingDir: File?

  val compilerArguments: List<String>?

  /** @see com.jetbrains.cidr.lang.workspace.OCCompilerSettings.getHeadersSearchPaths */
  val headerSearchPaths: List<CppHeaderSearchPath>?

  /** @see com.jetbrains.cidr.lang.workspace.OCCompilerSettings.getImplicitIncludes*/
  val implicitIncludes: List<FilePointer>?


  /** @see com.jetbrains.cidr.lang.workspace.OCCompilerSettings.getMappedInclude */
  val mappedIncludes: Map<String, FilePointer>?


  val preprocessorDefines: String? // or List<String> for better interning

  /** @see com.jetbrains.cidr.lang.workspace.OCCompilerSettings.getCompilerFeatures*/
  val compilerFeatures: Map<CppCompilerFeature, Any>

  /** @see com.jetbrains.cidr.lang.workspace.OCCompilerSettings.getCachingKey */
  val cachingKey: String

}

interface CppHeaderSearchPath {
  val path: FilePointer

  val type: Type
  val recursive: Boolean

  enum class Type {
    HEADERS, FRAMEWORKS
  }
}

interface CppCompilerFeature {
  /** @see com.jetbrains.cidr.lang.workspace.compiler.OCCompilerFeatures */
}