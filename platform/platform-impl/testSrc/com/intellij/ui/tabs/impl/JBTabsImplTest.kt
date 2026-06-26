package com.intellij.ui.tabs.impl

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.tabs.TabInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JPanel

@TestApplication
@RunInEdt
internal class JBTabsImplTest {
  @Test
  fun `remove hidden tab without changing selection removes tab`(@TestDisposable disposable: Disposable) {
    val tabs = JBTabsImpl(
      project = null,
      parentDisposable = disposable,
      tabListOptions = TabListOptions(),
    )
    val selectedTab = TabInfo(JPanel()).setText("selected")
    val hiddenTab = TabInfo(JPanel()).setText("hidden")
    tabs.addTab(selectedTab)
    tabs.addTab(hiddenTab)
    tabs.select(selectedTab, false)

    hiddenTab.isHidden = true
    tabs.removeTabWithoutChangingSelection(hiddenTab)

    assertThat(tabs.tabs).containsExactly(selectedTab)
    assertThat(tabs.getVisibleInfos()).containsExactly(selectedTab)
    assertThat(tabs.selectedInfo).isSameAs(selectedTab)
  }
}
