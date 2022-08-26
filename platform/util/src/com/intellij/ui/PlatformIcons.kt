// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus.Internal

@Internal
enum class PlatformIcons(@JvmField internal val testId: String? = null) {
  Public,
  Private,
  Protected,
  Local,

  TodoDefault,
  TodoQuestion,
  TodoImportant,

  NodePlaceholder,
  WarningDialog,
  Copy,
  TestStateRun,
  Import,
  Export,
  Stub,

  Package,
  Folder,
  IdeaModule,

  TextFileType,
  ArchiveFileType,
  UnknownFileType,
  CustomFileType,
  JavaClassFileType("fileTypes/javaClass.svg"),
  JspFileType,
  JavaModule,
  JavaFileType("fileTypes/java.svg"),
  PropertiesFileType,

  Variable,
  Field,
  Class,
  AbstractClass,
  AnonymousClass,
  ExceptionClass,
  Enum,
  Aspect,
  Annotation,
  Function,
  Interface,
  Method,
  AbstractMethod("nodes/abstractMethod.svg"),
  AbstractException,
  MethodReference,
  Parameter,
  Property,
  Tag,
  Lambda,
  Record,
  ClassInitializer,
  Plugin,
  PpWeb,

  StaticMark,
  FinalMark,
  TestMark,
  JunitTestMark,
  RunnableMark,
}