// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ignore.actions

import com.intellij.openapi.vcs.changes.ignore.actions.IgnoreFileActionGroup
import org.zmlx.hg4idea.ignore.lang.HgIgnoreFileType

class HgIgnoreFileActionGroup : IgnoreFileActionGroup(HgIgnoreFileType.INSTANCE)