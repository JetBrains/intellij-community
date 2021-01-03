// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.exceptons

import com.intellij.openapi.util.NlsContexts.DialogMessage

open class InvalidSdkException(@DialogMessage s : String) : Exception(s)
