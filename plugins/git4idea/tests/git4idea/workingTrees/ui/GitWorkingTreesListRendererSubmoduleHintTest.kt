// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.JBList
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.config.GitSaveChangesPolicy
import com.intellij.vcs.test.vcsTestProjectPathFixture
import git4idea.repo.getAndInit
import git4idea.test.gitPlatformContextFixture
import git4idea.update.GitSubmoduleProjectContext
import git4idea.update.gitSubmoduleProjectFixture
import com.intellij.util.ui.JBUI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Container
import javax.swing.JLabel

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorkingTreesListRendererSubmoduleHintTest {
  private val contextFixture = gitPlatformContextFixture(vcsTestProjectPathFixture(), saveChangesPolicy = GitSaveChangesPolicy.STASH)
    .gitSubmoduleProjectFixture(checkoutSubmoduleBranch = true, updateSubmoduleRepo = true)
  private val context: GitSubmoduleProjectContext get() = contextFixture.get()

  @Test
  fun `test submodule row renders a visible submodule hint label`(): Unit = with(context) {
    GitRepositoriesHolder.getAndInit(project)

    val row = GitWorktreesUiUtil.buildEntries(project)
      .filterIsInstance<GitWorktreeRow>()
      .single { it.gitWorkingTree.isMain && it.repository.root.path == sub.root.path }
    assertThat(row.repositoryKind).isEqualTo(GitRepositoryKind.SUBMODULE)

    val renderer = GitWorkingTreesListRenderer(project)
    val list = JBList<GitWorkingTreesListEntry>()
    val rendered = renderer.getListCellRendererComponent(list, row, 0, false, false) as Container
    rendered.size = rendered.preferredSize
    forceLayout(rendered)

    val labels = mutableListOf<JLabel>()
    collectLabels(rendered, labels)
    val hintLabel = labels.singleOrNull { it.text == "Submodule" }
    val nameLabelInTree = labels.single { it.text == row.gitWorkingTree.path.name }

    assertThat(hintLabel).describedAs("A label with the submodule hint text must exist in the rendered row").isNotNull()
    assertThat(hintLabel!!.isVisible).describedAs("The submodule hint label must be visible for a submodule row").isTrue()
    assertThat(hintLabel.width).describedAs("The submodule hint label must occupy non-zero width once laid out").isGreaterThan(0)
    assertThat(hintLabel.x).describedAs("The submodule hint label must sit right after the name label")
      .isEqualTo(nameLabelInTree.x + nameLabelInTree.width + JBUI.scale(4))
  }

  // A renderer component fetched directly from getListCellRendererComponent, without ever being attached to a
  // displayable window, never gets a real Container.validate() cascade (that requires a peer at every level, the
  // same way CellRendererPane's validate() does in the real IDE once the JList itself is showing). Force each
  // nested layout manager to run directly instead, mirroring what that cascade would produce.
  private fun forceLayout(container: Container) {
    container.doLayout()
    for (component in container.components) {
      if (component is Container) forceLayout(component)
    }
  }

  private fun collectLabels(container: Container, out: MutableList<JLabel>) {
    for (component in container.components) {
      if (component is JLabel) out.add(component)
      if (component is Container) collectLabels(component, out)
    }
  }
}
