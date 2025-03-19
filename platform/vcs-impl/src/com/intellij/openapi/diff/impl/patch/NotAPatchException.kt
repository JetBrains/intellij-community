// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch

import com.intellij.openapi.vcs.VcsBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class NotAPatchException : PatchSyntaxException(VcsBundle.message("patch.apply.not.patch"))
