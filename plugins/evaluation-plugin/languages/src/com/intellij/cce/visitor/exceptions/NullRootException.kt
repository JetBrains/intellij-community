// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor.exceptions

class NullRootException(filePath: String) : NullPointerException("For file $filePath root element not found.")