// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.ui.layout.*
import git4idea.config.UpdateMethod

fun LayoutBuilder.updateMethodButtonGroup(get: () -> UpdateMethod, set: (UpdateMethod) -> Unit) =
  buttonGroup(get, set) {
    getUpdateMethods().forEach { method ->
      row {
        radioButton(method.presentation).bindValue(method)
      }
    }
  }

internal fun getUpdateMethods(): List<UpdateMethod> = listOf(UpdateMethod.MERGE, UpdateMethod.REBASE)