// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import javax.swing.Icon

class KotlinIdeFileIconProviderService : KotlinIconProviderService() {
    override fun getFileIcon(): Icon = KotlinIcons.FILE

    override fun getBuiltInFileIcon(): Icon = KotlinIcons.FILE
}