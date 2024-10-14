// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui

import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.*
import git4idea.commands.GitShallowCloneOptions
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GitShallowCloneViewModel {
  val shallowClone = AtomicBooleanProperty(false)
  val depth = AtomicProperty("1")

  fun getShallowCloneOptions() = if (shallowClone.get()) GitShallowCloneOptions(depth.get().toIntOrNull() ?: 1) else null
}

@ApiStatus.Internal
object GitShallowCloneComponentFactory {
  fun appendShallowCloneRow(panel: Panel, vm: GitShallowCloneViewModel): Row = with(panel) {
    row {
      var shallowCloneCheckbox = this.checkBox(GitBundle.message("clone.dialog.shallow.clone"))
        .gap(RightGap.SMALL)
        .bindSelected(vm.shallowClone)

      val depthTextField = this.intTextField(1..Int.MAX_VALUE, 1)
        .bindText(vm.depth)
        .enabledIf(shallowCloneCheckbox.selected)
        .gap(RightGap.SMALL)

      depthTextField.component.toolTipText = GIT_CLONE_DEPTH_ARG

      @Suppress("DialogTitleCapitalization")
      label(GitBundle.message("clone.dialog.shallow.clone.depth"))
    }.bottomGap(BottomGap.SMALL)
  }

  private const val GIT_CLONE_DEPTH_ARG: @NlsSafe String = "--depth"
}
